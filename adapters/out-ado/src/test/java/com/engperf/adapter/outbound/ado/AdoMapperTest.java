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
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Maps recorded Azure DevOps JSON to the RawEvent contract the metric groups consume. */
class AdoMapperTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  /** Histórico lido, sem nenhum voto negativo. */
  private static final VoteHistory APPROVED = new VoteHistory(true, false);

  @Test
  void pullRequestMapsCycleFirstPassAndLink() {
    JsonNode pr = fixture("pr.json");
    JsonNode commits =
        json(
            "{\"value\":[{\"author\":{\"date\":\"2026-06-10T10:00:00Z\"},"
                + "\"changeCounts\":{\"Add\":10,\"Edit\":5,\"Delete\":2}},"
                + "{\"author\":{\"date\":\"2026-06-10T13:00:00Z\"},"
                + "\"changeCounts\":{\"Add\":3,\"Edit\":0,\"Delete\":0}}]}");
    RawEvent e = AdoMapper.pullRequest(pr, commits, false, APPROVED);

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

  /**
   * O card "com IA vs. sem IA" parte da coorte de PRs. Um PR não tem marcação própria: ele herda a
   * dos commits, e sem isso a coorte com IA fica sempre vazia e o gráfico só mostra um lado.
   */
  @Test
  void aPullRequestCarriesTheAiFlagItWasGiven() {
    JsonNode commits = json("{\"value\":[]}");

    assertThat(AdoMapper.pullRequest(fixture("pr.json"), commits, true, APPROVED).ai()).isTrue();
    assertThat(AdoMapper.pullRequest(fixture("pr.json"), commits, false, APPROVED).ai()).isFalse();
  }

  /**
   * O caso relatado em produção: o revisor rejeitou, o autor corrigiu, o revisor aprovou — e o
   * autor aparecia com 100% de assertividade, porque o PR só carrega o voto final.
   */
  @Test
  void aPullRequestRejectedBeforeApprovalIsNotFirstPass() {
    VoteHistory rejectedThenApproved = new VoteHistory(true, true);

    RawEvent e =
        AdoMapper.pullRequest(
            fixture("pr.json"), json("{\"value\":[]}"), false, rejectedThenApproved);

    assertThat(e.detail().get("first_pass")).isEqualTo("0");
  }

  @Test
  void anUnreadableHistoryIsNotCountedAsFirstPass() {
    RawEvent e =
        AdoMapper.pullRequest(
            fixture("pr.json"), json("{\"value\":[]}"), false, VoteHistory.UNKNOWN);

    assertThat(e.detail().get("first_pass")).isEqualTo("0");
  }

  /** Quem rejeitou e depois aprovou fez trabalho de review; emitir só a aprovação o apagaria. */
  @Test
  void aChangeRequestIsNotErasedByALaterApproval() {
    List<RawEvent> reviews = AdoMapper.reviews(fixture("pr.json"), new VoteHistory(true, true));

    assertThat(reviews)
        .singleElement()
        .satisfies(r -> assertThat(r.detail().get("decision")).isEqualTo("changes_requested"));
  }

  @Test
  void pullRequestWithoutCommitsIsExcludedFromFlowEfficiency() {
    RawEvent e = AdoMapper.pullRequest(fixture("pr.json"), json("{\"value\":[]}"), false, APPROVED);
    assertThat(e.detail().get("num")).isEqualTo("0"); // num=den=0 → contributes nothing to ratio
    assertThat(e.detail().get("den")).isEqualTo("0");
    assertThat(e.detail()).doesNotContainKey("lines"); // size is "no data", not a fake zero
  }

  @Test
  void reviewsMapReviewerVotesAndSkipNoVote() {
    List<RawEvent> reviews = AdoMapper.reviews(fixture("pr.json"), APPROVED);

    assertThat(reviews).hasSize(1); // carla (vote 0) is skipped
    RawEvent r = reviews.get(0);
    assertThat(r.type()).isEqualTo(EventType.REVIEW);
    assertThat(r.committerIdentity()).isEqualTo("bruno@empresa.com"); // reviewer
    assertThat(r.detail().get("author")).isEqualTo("ana@empresa.com"); // reviewed PR's author
    assertThat(r.detail().get("decision")).isEqualTo("approved");
    assertThat(r.detail().get("comments")).isEqualTo("2");
    assertThat(r.detail().get("url")).contains("pullrequest/42"); // links back to the PR
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

  /**
   * O mapeamento era um placeholder não documentado: Task caía sempre em "manutenção" (e Task é o
   * tipo mais numeroso da maioria dos boards, então o gráfico dizia que o time só fazia manutenção)
   * e Epic caía em dívida técnica.
   */
  @ParameterizedTest(name = "{0} (pai {1}) → {2}")
  @CsvSource({
    "Bug,,bug",
    "User Story,,feature",
    "Feature,,feature",
    "Product Backlog Item,,feature",
    "Epic,,feature",
    "Tech Debt,,tech_debt",
    "tech debt,,tech_debt",
    "TECH DEBT,,tech_debt",
    "Documentation or Other,,docs",
    "Impediment,,docs",
    "Task,User Story,feature",
    "Task,Epic,feature",
    "Task,Bug,bug",
    "Task,Tech Debt,tech_debt",
    "Task,Documentation or Other,docs",
    "task,user story,feature",
    "Task,,docs",
    "Task,Task,docs",
    "Task,Coisa Estranha,docs",
  })
  void workTypeMapping(String adoType, String parentType, String expected) {
    assertThat(AdoMapper.workType(adoType, parentType)).isEqualTo(expected);
  }

  @Test
  void aTaskWithABlankParentTypeFallsBack() {
    assertThat(AdoMapper.workType("Task", null)).isEqualTo("docs");
    assertThat(AdoMapper.workType("Task", "  ")).isEqualTo("docs");
  }

  /** Fiação: o tipo do pai chega ao detalhe do evento, que é o que o painel consome. */
  @Test
  void aTaskEventCarriesTheParentsWorkType() {
    RawEvent e =
        AdoMapper.workItem(
            fixture("workitem-task.json"),
            json("{\"value\":[]}"),
            state -> Segment.ACTIVE,
            Instant.parse("2026-06-30T12:00:00Z"),
            "org",
            "Proj",
            "Tech Debt");

    assertThat(e.detail().get("type")).isEqualTo("tech_debt");
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
    assertThat(e.detail().get("summary")).isEqualTo("20260610.3");
    assertThat(e.detail().get("url")).contains("_build/results?buildId=77");
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

  /** New → Blocked → Active → Code Review → Closed, classified by name into flow segments. */
  private static final Function<String, Segment> CLASSIFY =
      name ->
          switch (name) {
            case "Closed", "Done", "Removed" -> Segment.DONE;
            case "New", "Blocked" -> Segment.WAITING;
            case "Code Review" -> Segment.REVIEW;
            default -> Segment.ACTIVE;
          };

  @Test
  void workItemDerivesActiveWaitCycleAndLeadFromHistory() {
    JsonNode updates =
        updates(
            stateUpdate("2026-06-10T10:00:00Z", "New"), // backlog before work → ignored
            stateUpdate("2026-06-10T11:00:00Z", "Active"), // active 11:00 → 12:00
            stateUpdate("2026-06-10T12:00:00Z", "Blocked"), // wait 12:00 → 13:00
            stateUpdate("2026-06-10T13:00:00Z", "Code Review"), // review 13:00 → 14:00
            stateUpdate("2026-06-10T14:00:00Z", "Closed")); // completion
    Instant now = Instant.parse("2026-06-11T00:00:00Z");

    RawEvent e =
        AdoMapper.workItem(fixture("workitem.json"), updates, CLASSIFY, now, "org", "Proj", null);
    assertThat(e.id()).isEqualTo("wi:555");
    assertThat(e.type()).isEqualTo(EventType.WORKITEM);
    assertThat(e.committerIdentity()).isEqualTo("ana@empresa.com");
    assertThat(e.occurredAt().toString()).isEqualTo("2026-06-10T14:00:00Z"); // dated at completion
    assertThat(e.detail().get("summary")).isEqualTo("Checkout falha com CPF inválido");
    assertThat(e.detail().get("url")).isEqualTo("org/Proj/_workitems/edit/555");
    assertThat(e.detail().get("active_h")).isEqualTo("1.0"); // 11:00 → 12:00
    assertThat(e.detail().get("wait_h")).isEqualTo("1.0"); // Blocked 12:00 → 13:00
    assertThat(e.detail().get("review_h")).isEqualTo("1.0"); // Code Review 13:00 → 14:00
    assertThat(e.detail().get("cycle_h")).isEqualTo("3.0"); // first work 11:00 → done 14:00
    assertThat(e.detail().get("lead_h")).isEqualTo("5.0"); // created 09:00 → done 14:00
    assertThat(e.detail().get("completed")).isEqualTo("1");
    assertThat(e.detail().get("num")).isEqualTo("2.0"); // working = active + review
    assertThat(e.detail().get("den")).isEqualTo("3.0"); // working + wait
  }

  @Test
  void workItemWithNoUsableTransitionIsNoData() {
    JsonNode oneState = updates(stateUpdate("2026-06-10T10:00:00Z", "New"));
    RawEvent e =
        AdoMapper.workItem(
            fixture("workitem.json"),
            oneState,
            CLASSIFY,
            Instant.parse("2026-06-11T00:00:00Z"),
            "org",
            "Proj",
            null);
    assertThat(e.numericValue()).isNull(); // no usable history → excluded from the metric value
    assertThat(e.detail()).doesNotContainKey("active_h").doesNotContainKey("completed");
  }

  @Test
  void openItemInProgressCarriesActiveButNoCompletion() {
    JsonNode updates =
        updates(
            stateUpdate("2026-06-10T10:00:00Z", "New"),
            stateUpdate("2026-06-10T11:00:00Z", "Active")); // still active, never closed
    RawEvent e =
        AdoMapper.workItem(
            fixture("workitem.json"),
            updates,
            CLASSIFY,
            Instant.parse("2026-06-10T13:00:00Z"),
            "org",
            "Proj",
            null);
    assertThat(e.detail().get("in_progress")).isEqualTo("1");
    assertThat(e.detail()).doesNotContainKey("completed").doesNotContainKey("cycle_h");
    assertThat(e.detail().get("active_h")).isEqualTo("2.0"); // 11:00 → now 13:00
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
