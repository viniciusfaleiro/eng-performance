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
import java.util.function.Predicate;

/**
 * Pure mapping of Azure DevOps REST payloads (api-version 7.1) to the platform's {@link RawEvent}
 * contract — filling the {@code detail} keys the metric groups already consume. Unambiguous fields
 * (ids, identities, dates, decisions, types, deep-links) are mapped here and covered by fixture
 * tests; org-shaped derivations (fine phase split, deploy recovery pairing) are approximated and
 * tuned against a real org during acceptance.
 */
final class AdoMapper {

  private AdoMapper() {}

  /** A pull request → one PR event (cycle time + first-pass approval + deep-link). */
  static RawEvent pullRequest(JsonNode pr) {
    Instant created = instant(pr, "creationDate");
    Instant closed = pr.hasNonNull("closedDate") ? instant(pr, "closedDate") : created;
    double cycleH = hoursBetween(created, closed);
    String author = identity(pr.path("createdBy"));

    Map<String, String> detail = new HashMap<>();
    detail.put("cycle_h", num(cycleH));
    // Without commit-level timing we cannot split the four phases precisely; attribute the whole
    // cycle to review here and refine during acceptance. pr_review_time reads numericValue below.
    detail.put("review_h", num(cycleH));
    detail.put("first_pass", firstPass(pr) ? "1" : "0");
    detail.put("repo", pr.path("repository").path("name").asText(""));
    detail.put("summary", pr.path("title").asText(""));
    detail.put("url", webLink(pr));

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

  /** A pipeline run for the production stage → one DEPLOY event (empty otherwise). */
  static Optional<RawEvent> deploy(JsonNode run, String productionStage) {
    String stage = run.path("stageName").asText(run.path("environment").asText(""));
    if (!matchesProduction(stage, productionStage)) {
      return Optional.empty();
    }
    boolean failed = !"succeeded".equalsIgnoreCase(run.path("result").asText(""));
    Instant queued =
        run.hasNonNull("queueTime") ? instant(run, "queueTime") : instant(run, "finishTime");
    Instant finished = instant(run, "finishTime");
    Map<String, String> detail = new HashMap<>();
    detail.put("outcome", failed ? "failed" : "success");
    detail.put("num", failed ? "1" : "0"); // CFR numerator
    detail.put("den", "1");
    return Optional.of(
        new RawEvent(
            "deploy:" + run.path("id").asText(),
            EventType.DEPLOY,
            finished,
            run.path("repository")
                .path("name")
                .asText(run.path("pipeline").path("name").asText("")),
            null,
            hoursBetween(queued, finished), // lead time
            null,
            false,
            detail));
  }

  /** A work item → one WORKITEM event (type + completed hours) for the distribution. */
  static RawEvent workItem(JsonNode wi) {
    JsonNode f = wi.path("fields");
    Map<String, String> detail = new HashMap<>();
    detail.put("type", workType(f.path("System.WorkItemType").asText("")));
    detail.put("hours", num(f.path("Microsoft.VSTS.Scheduling.CompletedWork").asDouble(0)));
    return new RawEvent(
        "wi:" + wi.path("id").asText(),
        EventType.WORKITEM,
        instant(f, "System.ChangedDate"),
        null,
        identity(f.path("System.AssignedTo")),
        null,
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
      return stage.toLowerCase(Locale.ROOT).contains("prod");
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

  private static double hoursBetween(Instant a, Instant b) {
    return Math.max(0, Duration.between(a, b).toMinutes()) / 60.0;
  }

  private static String num(double v) {
    return Double.toString(Math.round(v * 100.0) / 100.0);
  }

  private static String firstLine(String s) {
    int nl = s.indexOf('\n');
    return nl < 0 ? s : s.substring(0, nl);
  }
}
