package com.engperf.adapter.outbound.ado;

import static org.assertj.core.api.Assertions.assertThat;

import com.engperf.domain.metrics.EventType;
import com.engperf.domain.metrics.RawEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/** Maps recorded Azure DevOps JSON to the RawEvent contract the metric groups consume. */
class AdoMapperTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void pullRequestMapsCycleFirstPassAndLink() {
    JsonNode pr = fixture("pr.json");
    JsonNode commits =
        json(
            "{\"value\":[{\"author\":{\"date\":\"2026-06-10T10:00:00Z\"},"
                + "\"changeCounts\":{\"Add\":10,\"Edit\":5,\"Delete\":2}},"
                + "{\"author\":{\"date\":\"2026-06-10T13:00:00Z\"},"
                + "\"changeCounts\":{\"Add\":3,\"Edit\":0,\"Delete\":0}}]}");
    RawEvent e = AdoMapper.pullRequest(pr, commits);

    assertThat(e.id()).isEqualTo("pr:42");
    assertThat(e.type()).isEqualTo(EventType.PR);
    assertThat(e.committerIdentity()).isEqualTo("ana@empresa.com");
    assertThat(e.occurredAt().toString()).isEqualTo("2026-06-10T15:00:00Z"); // closedDate
    assertThat(e.detail().get("cycle_h")).isEqualTo("6.0"); // 09:00 → 15:00
    assertThat(e.detail().get("first_pass")).isEqualTo("1"); // approved, no changes requested
    assertThat(e.detail().get("repo")).isEqualTo("checkout-service");
    assertThat(e.detail().get("url")).contains("pullrequest/42");
    assertThat(e.detail().get("coding_h")).isEqualTo("3.0"); // first 10:00 → last 13:00 commit
    assertThat(e.detail().get("lines")).isEqualTo("20"); // 10+5+2 + 3+0+0 changed lines
    assertThat(e.detail().get("num")).isEqualTo("3.0"); // flow_efficiency = coding 3h / cycle 6h
    assertThat(e.detail().get("den")).isEqualTo("6.0");
  }

  @Test
  void pullRequestWithoutCommitsIsExcludedFromFlowEfficiency() {
    RawEvent e = AdoMapper.pullRequest(fixture("pr.json"), json("{\"value\":[]}"));
    assertThat(e.detail().get("num")).isEqualTo("0"); // num=den=0 → contributes nothing to ratio
    assertThat(e.detail().get("den")).isEqualTo("0");
    assertThat(e.detail()).doesNotContainKey("lines"); // size is "no data", not a fake zero
  }

  @Test
  void reviewsMapReviewerVotesAndSkipNoVote() {
    List<RawEvent> reviews = AdoMapper.reviews(fixture("pr.json"));

    assertThat(reviews).hasSize(1); // carla (vote 0) is skipped
    RawEvent r = reviews.get(0);
    assertThat(r.type()).isEqualTo(EventType.REVIEW);
    assertThat(r.committerIdentity()).isEqualTo("bruno@empresa.com"); // reviewer
    assertThat(r.detail().get("author")).isEqualTo("ana@empresa.com"); // reviewed PR's author
    assertThat(r.detail().get("decision")).isEqualTo("approved");
    assertThat(r.detail().get("comments")).isEqualTo("2");
  }

  @Test
  void commitMapsIdentityAiFlagAndLink() {
    RawEvent e =
        AdoMapper.commit(
            fixture("commit.json"),
            "checkout-service",
            msg -> msg.toLowerCase(Locale.ROOT).contains("copilot"));

    assertThat(e.id()).isEqualTo("commit:abc123");
    assertThat(e.committerIdentity()).isEqualTo("ana@empresa.com");
    assertThat(e.ai()).isTrue(); // Co-authored-by: Copilot
    assertThat(e.detail().get("summary")).isEqualTo("fix: cpf no checkout");
    assertThat(e.detail().get("url")).contains("commit/abc123");
  }

  @Test
  void buildStageMapsToDeployOnlyForTheProductionStage() {
    JsonNode build = fixture("build.json");
    JsonNode prod =
        json(
            "{\"id\":\"s1\",\"type\":\"Stage\",\"name\":\"Production\",\"result\":\"succeeded\","
                + "\"finishTime\":\"2026-06-10T10:30:00Z\"}");
    JsonNode pending =
        json("{\"id\":\"s2\",\"type\":\"Stage\",\"name\":\"Production\",\"result\":\"\"}");

    assertThat(AdoMapper.deploy(build, prod, "Production")).isPresent();
    assertThat(AdoMapper.deploy(build, prod, "Staging")).isEmpty(); // stage "Production" != rule
    assertThat(AdoMapper.deploy(build, pending, "Production")).isEmpty(); // no result yet

    RawEvent e = AdoMapper.deploy(build, prod, "Production").orElseThrow();
    assertThat(e.type()).isEqualTo(EventType.DEPLOY);
    assertThat(e.id()).isEqualTo("deploy:77:s1"); // build id + stage record id
    assertThat(e.repoKey()).isEqualTo("checkout-service");
    assertThat(e.detail().get("outcome")).isEqualTo("success");
    assertThat(e.detail().get("num")).isEqualTo("0"); // not failed → CFR numerator 0
    assertThat(e.value()).isEqualTo(0.5); // lead: queue 10:00 → stage finish 10:30
  }

  @Test
  void blankRuleFallbackRecognizesPrdSpelling() {
    JsonNode build = fixture("build.json");
    JsonNode prd =
        json(
            "{\"id\":\"s1\",\"type\":\"Stage\",\"name\":\"Deploy to PRD\",\"result\":\"failed\","
                + "\"finishTime\":\"2026-06-10T10:30:00Z\"}");
    JsonNode hml =
        json(
            "{\"id\":\"s2\",\"type\":\"Stage\",\"name\":\"Deploy to HML\",\"result\":\"succeeded\"}");

    // No explicit production_stage → the fallback must accept "PRD" (not a substring of "prod").
    RawEvent e = AdoMapper.deploy(build, prd, "").orElseThrow();
    assertThat(e.detail().get("outcome")).isEqualTo("failed");
    assertThat(AdoMapper.deploy(build, hml, "")).isEmpty(); // HML is not production
  }

  @Test
  void workItemMapsTypeAndInProgressTimeFromHistory() {
    JsonNode updates =
        updates(
            stateUpdate("2026-06-10T10:00:00Z", "New"),
            stateUpdate("2026-06-10T11:00:00Z", "Active"),
            stateUpdate("2026-06-10T13:00:00Z", "Closed"));
    Predicate<String> inProgress = "Active"::equals;
    Instant now = Instant.parse("2026-06-11T00:00:00Z");

    RawEvent e = AdoMapper.workItem(fixture("workitem.json"), updates, inProgress, now);
    assertThat(e.id()).isEqualTo("wi:555");
    assertThat(e.type()).isEqualTo(EventType.WORKITEM);
    assertThat(e.committerIdentity()).isEqualTo("ana@empresa.com");
    assertThat(e.detail().get("type")).isEqualTo("bug");
    assertThat(e.value()).isEqualTo(2.0); // in Active 11:00 → Closed 13:00 = 2h in progress
    assertThat(e.detail().get("hours"))
        .isEqualTo("2.0"); // same value on the type-distribution channel
    long a = Instant.parse("2026-06-10T11:00:00Z").toEpochMilli();
    long b = Instant.parse("2026-06-10T13:00:00Z").toEpochMilli();
    assertThat(e.detail().get("spans"))
        .isEqualTo(a + ":" + b); // in-progress interval, for period-clipped views
  }

  @Test
  void workItemWithNoUsableTransitionIsNoData() {
    JsonNode oneState = updates(stateUpdate("2026-06-10T10:00:00Z", "New"));
    RawEvent e =
        AdoMapper.workItem(
            fixture("workitem.json"), oneState, s -> true, Instant.parse("2026-06-11T00:00:00Z"));
    assertThat(e.numericValue()).isNull(); // no usable history → excluded from the metric value
    assertThat(e.detail()).doesNotContainKey("hours");
  }

  @Test
  void inProgressHoursIsEmptyWhenAllTransitionsAtOneInstant() {
    JsonNode sameInstant =
        updates(
            stateUpdate("2026-06-10T10:00:00Z", "New"),
            stateUpdate("2026-06-10T10:00:00Z", "Closed"));
    assertThat(
            AdoMapper.inProgressHours(
                sameInstant, s -> true, Instant.parse("2026-06-11T00:00:00Z")))
        .isEmpty(); // created and closed together → no measurable data
  }

  private static String stateUpdate(String date, String state) {
    return "{\"revisedDate\":\""
        + date
        + "\",\"fields\":{\"System.State\":{\"newValue\":\""
        + state
        + "\"}}}";
  }

  private static JsonNode updates(String... entries) {
    return json("{\"value\":[" + String.join(",", entries) + "]}");
  }

  private static JsonNode fixture(String name) {
    try {
      return JSON.readTree(AdoMapperTest.class.getResourceAsStream("/ado/" + name));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static JsonNode json(String raw) {
    try {
      return JSON.readTree(raw);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
