package com.engperf.bootstrap.config;

import com.engperf.application.port.outbound.EventStorePort;
import com.engperf.domain.metrics.EventType;
import com.engperf.domain.metrics.RawEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Seeds ~6 months of synthetic raw events (idempotent, fully deterministic — no randomness, no
 * wall-clock) so the metrics engine and the Tendências view have data. Dates are anchored to the
 * fixed reference window (2026-01-01 … 2026-06-29). The real Azure DevOps sync (S9) replaces this.
 *
 * <p>Coverage is intentionally below 100%: unlinked committer identities (copilot) and an unmapped
 * repository (legacy-batch) produce unattributed events, exercising the data-quality badge.
 */
@Component
@Order(3)
class EventFixtures implements CommandLineRunner {

  private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
  private static final LocalDate TO = LocalDate.of(2026, 6, 30); // exclusive

  private static final String[] LINKED = {
    "ana.souza@empresa.com", "bruno.lima@empresa.com", "eduardo.alves@empresa.com"
  };
  private static final String[] MAPPED_REPOS = {
    "checkout-service",
    "pix-gateway",
    "antifraude-api",
    "core-banking",
    "sre-tooling",
    "growth-web",
    "retention-jobs"
  };

  private final EventStorePort events;

  EventFixtures(EventStorePort events) {
    this.events = events;
  }

  @Override
  public void run(String... args) {
    if (events.count() > 0) {
      return;
    }
    List<RawEvent> batch = new ArrayList<>();
    for (LocalDate day = FROM; day.isBefore(TO); day = day.plusDays(1)) {
      if (isWeekend(day)) {
        continue;
      }
      seedPersonEvents(batch, day);
      seedRepoEvents(batch, day);
    }
    events.saveAll(batch);
  }

  private void seedPersonEvents(List<RawEvent> batch, LocalDate day) {
    String d = day.toString();
    for (String identity : LINKED) {
      int prs = pick(identity + "|pr|" + d, 3); // 0..2 PRs
      for (int i = 0; i < prs; i++) {
        double coding = 2 + pick(identity + "|cod|" + d + i, 10); // 2..11h
        double pickup = 1 + pick(identity + "|pick|" + d + i, 6); // 1..6h
        double review = 1 + pick(identity + "|rev|" + d + i, 8); // 1..8h
        double deploy = 1 + pick(identity + "|dep|" + d + i, 4); // 1..4h
        int lines = 30 + pick(identity + "|lines|" + d + i, 400); // 30..429
        batch.add(pr(identity, day, i, coding, pickup, review, deploy, lines));
      }
      int commits = pick(identity + "|c|" + d, 4); // 0..3 commits
      for (int i = 0; i < commits; i++) {
        boolean ai = pick(identity + "|ai|" + d + i, 2) == 1;
        batch.add(commit(identity, day, i, ai));
      }
      // One WIP gauge per active person per day (snapshot metric).
      batch.add(workitem(identity, day, 3 + pick(identity + "|wip|" + d, 8)));
    }
    // Unlinked identity (copilot) commits → unattributed, drops ai_share/commit coverage.
    int botCommits = pick("copilot|" + d, 3);
    for (int i = 0; i < botCommits; i++) {
      batch.add(commit("copilot@github.com", day, i, true));
    }
  }

  private void seedRepoEvents(List<RawEvent> batch, LocalDate day) {
    String d = day.toString();
    for (String repo : MAPPED_REPOS) {
      int deploys = pick(repo + "|dep|" + d, 2); // 0..1 deploy
      for (int i = 0; i < deploys; i++) {
        double lead = 8 + pick(repo + "|lead|" + d + i, 60);
        boolean failed = pick(repo + "|fail|" + d + i, 6) == 0; // ~1 in 6 fails
        if (failed) {
          batch.add(deploy(repo, day, i, "failed", lead, null));
          // Each failure is paired with a recovery deploy carrying its restore duration (MTTR).
          double recoveryHours = 1 + pick(repo + "|rec|" + d + i, 8); // 1..8h
          batch.add(deploy(repo, day, i, "recovery", lead, recoveryHours));
        } else {
          batch.add(deploy(repo, day, i, "success", lead, null));
        }
      }
    }
    // Unmapped repo deploys → unattributed, drops deploy coverage.
    if (pick("legacy|" + d, 3) == 0) {
      batch.add(deploy("legacy-batch", day, 0, "success", 20, null));
    }
  }

  private static RawEvent pr(
      String identity,
      LocalDate day,
      int i,
      double coding,
      double pickup,
      double review,
      double deploy,
      int lines) {
    double cycle = coding + pickup + review + deploy;
    double active = coding + review; // coding + review; pickup + deploy are waiting
    Map<String, String> detail = new java.util.HashMap<>();
    detail.put("coding_h", Double.toString(coding));
    detail.put("pickup_h", Double.toString(pickup));
    detail.put("review_h", Double.toString(review));
    detail.put("deploy_h", Double.toString(deploy));
    detail.put("cycle_h", Double.toString(cycle));
    detail.put("lines", Integer.toString(lines));
    detail.put("num", Double.toString(active)); // flow_efficiency numerator
    detail.put("den", Double.toString(cycle)); // flow_efficiency denominator
    // numericValue = review hours so pr_review_time (measure=value) reads it directly.
    return new RawEvent(
        id("pr", identity, day, i),
        EventType.PR,
        at(day),
        null,
        identity,
        review,
        "review",
        false,
        detail);
  }

  private static RawEvent commit(String identity, LocalDate day, int i, boolean ai) {
    return new RawEvent(
        id("commit", identity, day, i),
        EventType.COMMIT,
        at(day),
        null,
        identity,
        null,
        null,
        ai,
        Map.of());
  }

  private static RawEvent workitem(String identity, LocalDate day, double wip) {
    return new RawEvent(
        id("wip", identity, day, 0),
        EventType.WORKITEM,
        at(day),
        null,
        identity,
        wip,
        null,
        false,
        Map.of());
  }

  private static RawEvent deploy(
      String repo, LocalDate day, int i, String outcome, double lead, Double recoveryHours) {
    boolean recovery = "recovery".equals(outcome);
    Map<String, String> detail = new java.util.HashMap<>();
    detail.put("outcome", outcome);
    detail.put("num", "failed".equals(outcome) ? "1" : "0"); // CFR numerator
    detail.put("den", "1");
    if (recoveryHours != null) {
      detail.put("recovery_hours", Double.toString(recoveryHours));
    }
    return new RawEvent(
        id(recovery ? "rec" : "dep", repo, day, i),
        EventType.DEPLOY,
        recovery ? day.atTime(14, 0).toInstant(ZoneOffset.UTC) : at(day),
        repo,
        null,
        lead,
        null,
        false,
        detail);
  }

  private static String id(String kind, String owner, LocalDate day, int i) {
    return kind + ":" + owner + ":" + day + ":" + i;
  }

  private static Instant at(LocalDate day) {
    return day.atTime(10, 0).toInstant(ZoneOffset.UTC);
  }

  private static boolean isWeekend(LocalDate day) {
    return switch (day.getDayOfWeek()) {
      case SATURDAY, SUNDAY -> true;
      default -> false;
    };
  }

  /** Deterministic 0..(bound-1) from a string key — String.hashCode is specified/stable. */
  private static int pick(String key, int bound) {
    return Math.floorMod(key.hashCode(), bound);
  }
}
