package com.engperf.adapter.outbound.ado;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Flow measures reconstructed from a work item's state-transition history: active/review/wait
 * hours, cycle (first working state → terminal) and lead (creation → completion), the completion
 * instant, and the ACTIVE+REVIEW {@code spans} the individual distribution prorates. Backlog time
 * before the first working state is ignored; wait counts only idle time between the first working
 * and terminal.
 *
 * <p>Every step is traced at DEBUG, keyed by the work item id: the reconstruction depends on the
 * item's own state names and on revision dates the API sometimes reports oddly, so when a number
 * looks wrong the only useful answer is the sequence that produced it. Raising the level for this
 * package and grepping the id gives the whole history — no ids compiled in, no rebuild.
 */
record WorkItemFlow(
    double activeH,
    double reviewH,
    double waitH,
    Double cycleH,
    Double leadH,
    Instant completion,
    boolean started,
    String spans) {

  private static final Logger LOG = LoggerFactory.getLogger(WorkItemFlow.class);

  static WorkItemFlow of(
      String wiId,
      JsonNode updates,
      Function<String, Segment> classify,
      Instant created,
      Instant changed,
      Instant now) {
    List<StateAt> states = collectStates(wiId, updates, classify, changed, now);
    if (states.size() < 2) {
      LOG.debug(
          "WI {}: {} transição(ões) utilizável(is) — sem flow (mínimo 2)", wiId, states.size());
      return new WorkItemFlow(0, 0, 0, null, null, null, false, "");
    }
    states.sort(Comparator.comparing(StateAt::at));
    double activeMs = 0;
    double reviewMs = 0;
    double waitMs = 0;
    Instant firstWork = null;
    Instant completion = null;
    StringJoiner spans = new StringJoiner(",");
    for (int i = 0; i < states.size(); i++) {
      Segment seg = states.get(i).segment();
      Instant from = states.get(i).at();
      if (seg == Segment.DONE) {
        completion = from;
        LOG.debug("WI {}: DONE em {} — encerra a contagem", wiId, from);
        break;
      }
      Instant to = i + 1 < states.size() ? states.get(i + 1).at() : now;
      if (!from.isBefore(to)) {
        continue;
      }
      double ms = to.toEpochMilli() - (double) from.toEpochMilli();
      if (working(seg)) {
        if (firstWork == null) {
          firstWork = from;
        }
        spans.add(from.toEpochMilli() + ":" + to.toEpochMilli());
        activeMs += seg == Segment.ACTIVE ? ms : 0;
        reviewMs += seg == Segment.REVIEW ? ms : 0;
      } else if (firstWork != null) {
        waitMs += ms; // idle during the flow; backlog before the first work is ignored
      }
    }
    LOG.debug(
        "WI {}: flow ativo={}h review={}h espera={}h conclusão={}",
        wiId,
        activeMs / 3_600_000.0,
        reviewMs / 3_600_000.0,
        waitMs / 3_600_000.0,
        completion);
    return new WorkItemFlow(
        activeMs / 3_600_000.0,
        reviewMs / 3_600_000.0,
        waitMs / 3_600_000.0,
        span(firstWork, completion),
        span(created, completion),
        completion,
        firstWork != null,
        spans.toString());
  }

  void fill(Map<String, String> detail) {
    if (!started && completion == null) {
      return; // no usable working/terminal transition → "no data"
    }
    double active = activeH + reviewH;
    detail.put("active_h", AdoMapper.num(activeH));
    detail.put("review_h", AdoMapper.num(reviewH));
    detail.put("wait_h", AdoMapper.num(waitH));
    detail.put("num", AdoMapper.num(active)); // flow_efficiency numerator = working time
    detail.put("den", AdoMapper.num(active + waitH)); //          denominator = working + wait
    detail.put("hours", AdoMapper.num(active)); // individual distribution total (active work)
    detail.put("spans", spans);
    if (completion == null) {
      detail.put("in_progress", "1");
      return;
    }
    detail.put("completed", "1");
    if (leadH != null) {
      detail.put("lead_h", AdoMapper.num(leadH));
    }
    if (cycleH != null) {
      detail.put("cycle_h", AdoMapper.num(cycleH));
    }
  }

  private static boolean working(Segment seg) {
    return seg == Segment.ACTIVE || seg == Segment.REVIEW;
  }

  private static Double span(Instant from, Instant to) {
    return (from != null && to != null) ? AdoMapper.hoursBetween(from, to) : null;
  }

  /**
   * The state transitions, with the current revision's timestamp recovered.
   *
   * <p>Azure DevOps stamps the revision that has not been superseded yet with {@code revisedDate =
   * 9999-01-01} — an "open end date", not a missing one. Dropping it used to discard the most
   * important transition of all: for an item closed and then left alone, the move to the terminal
   * state <em>is</em> the current revision, so every cleanly-finished item lost its completion and
   * never counted as delivered.
   *
   * <p>{@code changed} ({@code System.ChangedDate}) stands in for it. It is an approximation — it
   * marks the item's last change of any field, not specifically the state change — but when the
   * last thing that happened was the closing transition, which is the case this repairs, the two
   * are the same instant.
   */
  private static List<StateAt> collectStates(
      String wiId,
      JsonNode updates,
      Function<String, Segment> classify,
      Instant changed,
      Instant now) {
    List<StateAt> states = new ArrayList<>();
    for (JsonNode u : updates.path("value")) {
      JsonNode sv = u.path("fields").path("System.State");
      if (!sv.hasNonNull("newValue") || !u.hasNonNull("revisedDate")) {
        continue;
      }
      String raw = u.path("revisedDate").asText();
      Instant at = AdoMapper.parseInstant(raw);
      if (at == null) {
        LOG.debug("WI {}: revisedDate ilegível ({}) — transição descartada", wiId, raw);
        continue; // sem data e sem substituto: não dá para situar a transição no tempo
      }
      if (at.isAfter(now)) {
        if (changed == null || changed.isAfter(now)) {
          LOG.debug(
              "WI {}: revisão vigente ({}) sem ChangedDate utilizável — transição descartada",
              wiId,
              raw);
          continue;
        }
        LOG.debug("WI {}: revisão vigente ({}) datada pelo ChangedDate {}", wiId, raw, changed);
        at = changed;
      }
      String state = sv.path("newValue").asText("");
      Segment seg = classify.apply(state);
      LOG.debug("WI {}: transição {} → {} em {}", wiId, state, seg, at);
      states.add(new StateAt(at, seg));
    }
    return states;
  }

  private record StateAt(Instant at, Segment segment) {}
}
