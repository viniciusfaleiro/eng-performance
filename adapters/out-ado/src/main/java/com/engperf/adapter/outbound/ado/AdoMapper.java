package com.engperf.adapter.outbound.ado;

import com.engperf.domain.metrics.EventType;
import com.engperf.domain.metrics.RawEvent;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Pure mapping of Azure DevOps REST payloads (api-version 7.1) to {@link RawEvent}, filling the
 * {@code detail} keys the metric groups consume. Org-shaped derivations are tuned against a real
 * org.
 */
final class AdoMapper {

  private AdoMapper() {}

  /**
   * A pull request → one PR event; {@code commits} feed the coding time, flow ratio and PR size.
   */
  static RawEvent pullRequest(JsonNode pr, JsonNode commits) {
    Instant created = instant(pr, "creationDate");
    Instant closed = pr.hasNonNull("closedDate") ? instant(pr, "closedDate") : created;
    double cycleH = hoursBetween(created, closed);
    String author = identity(pr.path("createdBy"));

    Map<String, String> detail = new HashMap<>();
    detail.put("cycle_h", num(cycleH));
    detail.put("first_pass", firstPass(pr) ? "1" : "0");
    detail.put("repo", pr.path("repository").path("name").asText(""));
    detail.put("summary", pr.path("title").asText(""));
    detail.put("url", webLink(pr));
    applyCommitStats(detail, commits, cycleH);

    return new RawEvent(
        "pr:" + pr.path("pullRequestId").asLong(),
        EventType.PR,
        closed,
        null,
        author,
        cycleH, // numericValue → pr_review_time reads it
        "review",
        false,
        detail);
  }

  /**
   * From the PR's commits: {@code coding_h} = first→last commit; flow {@code num/den} =
   * coding/cycle; {@code lines} = summed {@code changeCounts} (else commit count). Best-effort,
   * acceptance-tuned; a PR with no commit data is excluded from flow_efficiency (num=den=0) and has
   * no {@code lines}.
   */
  private static void applyCommitStats(
      Map<String, String> detail, JsonNode commits, double cycleH) {
    Instant first = null;
    Instant last = null;
    long lines = 0;
    boolean hasCounts = false;
    int count = 0;
    for (JsonNode c : commits.path("value")) {
      Instant at = commitDate(c);
      if (at == null) {
        continue;
      }
      count++;
      first = (first == null || at.isBefore(first)) ? at : first;
      last = (last == null || at.isAfter(last)) ? at : last;
      JsonNode cc = c.path("changeCounts");
      if (cc.isObject()) {
        hasCounts = true;
        lines += cc.path("Add").asLong(0) + cc.path("Edit").asLong(0) + cc.path("Delete").asLong(0);
      }
    }
    if (count == 0) {
      detail.put("num", "0"); // no commit data → contributes nothing to flow_efficiency
      detail.put("den", "0");
      return;
    }
    double codingH = hoursBetween(first, last);
    detail.put("coding_h", num(codingH));
    detail.put("num", num(codingH)); // flow_efficiency numerator = active coding time
    detail.put("den", num(cycleH)); //                  denominator = whole cycle
    detail.put("lines", Long.toString(hasCounts ? lines : count));
  }

  private static Instant commitDate(JsonNode c) {
    JsonNode author = c.path("author");
    if (author.hasNonNull("date")) {
      return parseInstant(author.path("date").asText());
    }
    JsonNode committer = c.path("committer");
    return committer.hasNonNull("date") ? parseInstant(committer.path("date").asText()) : null;
  }

  /** A pull request's reviewer votes → one REVIEW event each (given by the reviewer). */
  static List<RawEvent> reviews(JsonNode pr) {
    List<RawEvent> out = new ArrayList<>();
    String author = identity(pr.path("createdBy"));
    Instant when =
        pr.hasNonNull("closedDate") ? instant(pr, "closedDate") : instant(pr, "creationDate");
    long prId = pr.path("pullRequestId").asLong();
    for (JsonNode r : pr.path("reviewers")) {
      int vote = r.path("vote").asInt(0);
      if (vote == 0) {
        continue; // no explicit vote → not a review action
      }
      Map<String, String> detail = new HashMap<>();
      detail.put("decision", vote >= 5 ? "approved" : "changes_requested");
      detail.put("comments", Integer.toString(r.path("commentCount").asInt(0)));
      detail.put("author", author);
      out.add(
          new RawEvent(
              "review:" + prId + ":" + identity(r),
              EventType.REVIEW,
              when,
              null,
              identity(r),
              null,
              null,
              false,
              detail));
    }
    return out;
  }

  /** A commit → one COMMIT event; {@code isAi} decides the AI flag from the commit message. */
  static RawEvent commit(JsonNode c, String repo, Predicate<String> isAi) {
    String message = c.path("comment").asText("");
    Map<String, String> detail = new HashMap<>();
    detail.put("repo", repo);
    detail.put("summary", firstLine(message));
    detail.put("url", c.path("remoteUrl").asText(""));
    return new RawEvent(
        "commit:" + c.path("commitId").asText(),
        EventType.COMMIT,
        instant(c.path("author"), "date"),
        null,
        identityEmail(c.path("author")),
        null,
        null,
        isAi.test(message),
        detail);
  }

  /**
   * A build Timeline "Stage" record matching the production rule → one DEPLOY event (stage name,
   * result and timing from {@code stageRecord}; repo and trigger from {@code build}). Empty when
   * the stage isn't production or hasn't finished.
   */
  static Optional<RawEvent> deploy(JsonNode build, JsonNode stageRecord, String productionStage) {
    String stage = stageRecord.path("name").asText(stageRecord.path("identifier").asText(""));
    if (!matchesProduction(stage, productionStage)) {
      return Optional.empty();
    }
    String result = stageRecord.path("result").asText("");
    if (result.isBlank()) {
      return Optional.empty(); // stage skipped/pending/still running → not a deploy yet
    }
    boolean failed = !"succeeded".equalsIgnoreCase(result);
    Instant queued =
        build.hasNonNull("queueTime")
            ? instant(build, "queueTime")
            : instant(stageRecord, "startTime");
    Instant finished =
        stageRecord.hasNonNull("finishTime")
            ? instant(stageRecord, "finishTime")
            : instant(build, "finishTime");
    Map<String, String> detail = new HashMap<>();
    detail.put("outcome", failed ? "failed" : "success");
    detail.put("num", failed ? "1" : "0"); // CFR numerator
    detail.put("den", "1");
    detail.put("stage", stage);
    return Optional.of(
        new RawEvent(
            "deploy:" + build.path("id").asText() + ":" + stageRecord.path("id").asText(),
            EventType.DEPLOY,
            finished,
            build
                .path("repository")
                .path("name")
                .asText(build.path("pipeline").path("name").asText("")),
            null,
            hoursBetween(queued, finished), // lead time: trigger → production finish
            null,
            false,
            detail));
  }

  /** A work item → one WORKITEM event, its flow measures reconstructed from the state history. */
  static RawEvent workItem(
      JsonNode wi, JsonNode updates, Function<String, Segment> classify, Instant now) {
    JsonNode f = wi.path("fields");
    Instant created = f.hasNonNull("System.CreatedDate") ? instant(f, "System.CreatedDate") : null;
    Map<String, String> detail = new HashMap<>();
    detail.put("type", workType(f.path("System.WorkItemType").asText("")));
    WorkItemFlow flow = WorkItemFlow.of(updates, classify, created, now);
    Instant occurred =
        flow.completion() != null ? flow.completion() : instant(f, "System.ChangedDate");
    flow.fill(detail);
    return new RawEvent(
        "wi:" + wi.path("id").asText(),
        EventType.WORKITEM,
        occurred,
        null,
        identity(f.path("System.AssignedTo")),
        null, // WORKITEM metrics read named detail keys, not numericValue
        null,
        false,
        detail);
  }

  // ---- helpers ----

  private static boolean firstPass(JsonNode pr) {
    boolean approved = false;
    for (JsonNode r : pr.path("reviewers")) {
      int vote = r.path("vote").asInt(0);
      if (vote < 0) {
        return false; // someone asked for changes → not first pass
      }
      if (vote >= 10) {
        approved = true;
      }
    }
    return approved;
  }

  private static boolean matchesProduction(String stage, String rule) {
    if (rule == null || rule.isBlank()) {
      // No explicit rule: match the common production spellings. "prd" (without the "o") is a
      // frequent convention and is NOT a substring of "prod", so check for it explicitly.
      String s = stage.toLowerCase(Locale.ROOT);
      return s.contains("prod") || s.contains("prd");
    }
    return stage.equalsIgnoreCase(rule)
        || stage.toLowerCase(Locale.ROOT).contains(rule.toLowerCase(Locale.ROOT));
  }

  private static String workType(String adoType) {
    return switch (adoType.toLowerCase(Locale.ROOT)) {
      case "bug" -> "bug";
      case "user story", "feature", "product backlog item" -> "feature";
      case "task" -> "maintenance";
      case "epic" -> "tech_debt";
      default -> "docs";
    };
  }

  private static String identity(JsonNode person) {
    String unique = person.path("uniqueName").asText("");
    return unique.isBlank() ? person.path("id").asText("") : unique;
  }

  private static String identityEmail(JsonNode author) {
    return author.path("email").asText(author.path("name").asText(""));
  }

  private static String webLink(JsonNode node) {
    return node.path("_links").path("web").path("href").asText("");
  }

  private static Instant instant(JsonNode node, String field) {
    return Instant.parse(node.path(field).asText());
  }

  static Instant parseInstant(String raw) {
    try {
      return Instant.parse(raw);
    } catch (RuntimeException e) {
      return null;
    }
  }

  static double hoursBetween(Instant a, Instant b) {
    return Math.max(0, Duration.between(a, b).toMinutes()) / 60.0;
  }

  static String num(double v) {
    return Double.toString(Math.round(v * 100.0) / 100.0);
  }

  private static String firstLine(String s) {
    int nl = s.indexOf('\n');
    return nl < 0 ? s : s.substring(0, nl);
  }
}
