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
  private static final String ADO = "https://dev.azure.com/minhaorg";
  private static final String[] MSGS = {
    "fix: validação de CPF no checkout",
    "feat: retry no gateway de pagamento",
    "refactor: extrai serviço de antifraude",
    "test: cobre cenários de timeout",
    "chore: atualiza dependências",
    "feat: cache de sessão no core banking",
    "perf: reduz N+1 na listagem",
    "fix: corrige flaky test de integração"
  };

  /** Work-item types (individual work distribution), in the panel's legend order. */
  private static final String[] WORK_TYPES = {"feature", "bug", "tech_debt", "maintenance", "docs"};

  private final EventStorePort events;
  private final com.engperf.application.port.inbound.PlatformConfigUseCase config;

  EventFixtures(
      EventStorePort events, com.engperf.application.port.inbound.PlatformConfigUseCase config) {
    this.events = events;
    this.config = config;
  }

  @Override
  public void run(String... args) {
    // Dev-only seed: once the real Azure DevOps integration is connected (S9), the sync fills
    // raw_event instead, so the seeder stands down.
    if (config.adoIntegration().connected() || events.count() > 0) {
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
    for (int p = 0; p < LINKED.length; p++) {
      String identity = LINKED[p];
      int prs = pick(identity + "|pr|" + d, 3); // 0..2 PRs
      for (int i = 0; i < prs; i++) {
        double coding = 2 + pick(identity + "|cod|" + d + i, 10); // 2..11h
        double pickup = 1 + pick(identity + "|pick|" + d + i, 6); // 1..6h
        double review = 1 + pick(identity + "|rev|" + d + i, 8); // 1..8h
        double deploy = 1 + pick(identity + "|dep|" + d + i, 4); // 1..4h
        int lines = 30 + pick(identity + "|lines|" + d + i, 400); // 30..429
        // The PR is AI-assisted when its commits used AI (convention) — modelled deterministically.
        boolean ai = pick(identity + "|prai|" + d + i, 2) == 1;
        boolean firstPass = pick(identity + "|fp|" + d + i, 3) != 0; // ~2/3 approved first pass
        batch.add(
            pr(
                identity,
                day,
                i,
                new double[] {coding, pickup, review, deploy},
                lines,
                ai,
                firstPass));
      }
      int commits = pick(identity + "|c|" + d, 4); // 0..3 commits
      for (int i = 0; i < commits; i++) {
        boolean ai = pick(identity + "|ai|" + d + i, 2) == 1;
        batch.add(commit(identity, day, i, ai));
      }
      // One work item per active person per day: WIP snapshot + a task type & effort hours.
      batch.add(workitem(identity, day));
      // Reviews the person gives on a colleague's PRs (drives reviews given/received).
      int reviews = pick(identity + "|rv|" + d, 3); // 0..2 reviews given
      for (int i = 0; i < reviews; i++) {
        String author = LINKED[(p + 1 + i) % LINKED.length]; // always a colleague
        boolean approved = pick(identity + "|rvd|" + d + i, 4) != 0; // ~3/4 approved
        int comments = pick(identity + "|rvc|" + d + i, 6); // 0..5 comments
        batch.add(review(identity, author, day, i, approved, comments));
      }
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
      double[] phases,
      int lines,
      boolean ai,
      boolean firstPass) {
    double coding = phases[0];
    double pickup = phases[1];
    double review = phases[2];
    double deploy = phases[3];
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
    detail.put("first_pass", firstPass ? "1" : "0"); // PR assertiveness
    addActivity(detail, "pr", identity, day, i);
    // numericValue = review hours so pr_review_time (measure=value) reads it directly.
    return new RawEvent(
        id("pr", identity, day, i),
        EventType.PR,
        at(day),
        null,
        identity,
        review,
        "review",
        ai,
        detail);
  }

  private static RawEvent commit(String identity, LocalDate day, int i, boolean ai) {
    Map<String, String> detail = new java.util.HashMap<>();
    addActivity(detail, "commit", identity, day, i);
    return new RawEvent(
        id("commit", identity, day, i),
        EventType.COMMIT,
        at(day),
        null,
        identity,
        null,
        null,
        ai,
        detail);
  }

  private static RawEvent workitem(String identity, LocalDate day) {
    String d = day.toString();
    double wip = 3 + pick(identity + "|wip|" + d, 8);
    Map<String, String> detail = new java.util.HashMap<>();
    detail.put("type", WORK_TYPES[pick(identity + "|wtype|" + d, WORK_TYPES.length)]);
    detail.put("hours", Double.toString(2 + pick(identity + "|whrs|" + d, 8))); // 2..9h
    return new RawEvent(
        id("wip", identity, day, 0),
        EventType.WORKITEM,
        at(day),
        null,
        identity,
        wip,
        null,
        false,
        detail);
  }

  private static RawEvent review(
      String reviewer, String author, LocalDate day, int i, boolean approved, int comments) {
    Map<String, String> detail = new java.util.HashMap<>();
    detail.put("decision", approved ? "approved" : "changes_requested");
    detail.put("comments", Integer.toString(comments));
    detail.put("author", author);
    return new RawEvent(
        id("review", reviewer, day, i),
        EventType.REVIEW,
        at(day),
        null,
        reviewer,
        null,
        null,
        false,
        detail);
  }

  /** Adds the repo/summary/deep-link fields the activity drawer reads for a commit or PR. */
  private static void addActivity(
      Map<String, String> detail, String kind, String owner, LocalDate day, int i) {
    String repo = MAPPED_REPOS[pick(owner + "|repo|" + day + i, MAPPED_REPOS.length)];
    detail.put("repo", repo);
    detail.put("summary", MSGS[pick(owner + "|msg|" + kind + day + i, MSGS.length)]);
    String ref = kind.equals("pr") ? "pullrequest" : "commit";
    String hex = Integer.toHexString((owner + kind + day + i).hashCode() & 0x7fffffff);
    detail.put("url", ADO + "/" + repo + "/_git/" + repo + "/" + ref + "/" + hex);
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
