package com.engperf.adapter.outbound.ado;

import com.engperf.domain.config.ImpedimentConvention;
import com.engperf.domain.metrics.Interval;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
 * <p>On top of that classification sits the impediment overlay ({@link ImpedimentTimeline}): the
 * part of an active or review stretch during which the item was impeded is reported as wait, since
 * a board with no blocked column otherwise reports an item parked for days inside an active column
 * as pure work. The overlay only moves hours between buckets — the three still sum to the same
 * total — and a stretch already classified as wait is left alone, because counting the overlay on
 * top of it would report more hours than the item lived.
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
    String spans,
    Map<String, Double> impededByReason,
    Map<String, String> impededLabels,
    String impededSpans) {

  private static final Logger LOG = LoggerFactory.getLogger(WorkItemFlow.class);

  static WorkItemFlow of(
      String wiId,
      JsonNode updates,
      Function<String, Segment> classify,
      Instant created,
      Instant changed,
      Instant now,
      ImpedimentConvention impediment) {
    List<StateAt> states = collectStates(wiId, updates, classify, changed, now);
    if (states.size() < 2) {
      LOG.debug(
          "WI {}: {} transição(ões) utilizável(is) — sem flow (mínimo 2)", wiId, states.size());
      return new WorkItemFlow(0, 0, 0, null, null, null, false, "", Map.of(), Map.of(), "");
    }
    ImpedimentTimeline blocked = ImpedimentTimeline.of(wiId, updates, impediment, changed, now);
    states.sort(Comparator.comparing(StateAt::at));
    double activeMs = 0;
    double reviewMs = 0;
    double waitMs = 0;
    Instant firstWork = null;
    Instant completion = null;
    StringJoiner spans = new StringJoiner(",");
    List<Interval> counted = new ArrayList<>();
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
        double impededMs = blocked.overlapMs(from, to);
        double workMs = ms - impededMs;
        activeMs += seg == Segment.ACTIVE ? workMs : 0;
        reviewMs += seg == Segment.REVIEW ? workMs : 0;
        waitMs += impededMs; // o item estava em coluna de trabalho, mas o trabalho estava parado
        counted.add(new Interval(from.toEpochMilli(), to.toEpochMilli()));
      } else if (firstWork != null) {
        waitMs += ms; // idle during the flow; backlog before the first work is ignored
        // Sem overlapMs: já é 100% espera. O intervalo entra na janela só para que o impedimento
        // que ocorreu aqui ganhe motivo na quebra — sem ser somado a waitMs uma segunda vez.
        counted.add(new Interval(from.toEpochMilli(), to.toEpochMilli()));
      }
    }
    LOG.debug(
        "WI {}: flow ativo={}h review={}h espera={}h conclusão={}",
        wiId,
        activeMs / 3_600_000.0,
        reviewMs / 3_600_000.0,
        waitMs / 3_600_000.0,
        completion);
    Map<String, Double> byReason = blocked.hoursByReason(counted);
    Map<String, String> labels = new LinkedHashMap<>();
    byReason.keySet().forEach(r -> labels.put(r, blocked.label(r)));
    return new WorkItemFlow(
        activeMs / 3_600_000.0,
        reviewMs / 3_600_000.0,
        waitMs / 3_600_000.0,
        span(firstWork, completion),
        span(created, completion),
        completion,
        firstWork != null,
        spans.toString(),
        byReason,
        labels,
        joinSpans(blocked.blocked()));
  }

  private static String joinSpans(List<Interval> intervals) {
    StringJoiner out = new StringJoiner(",");
    for (Interval i : intervals) {
      out.add(i.start() + ":" + i.end());
    }
    return out.toString();
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
    fillImpediment(detail);
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

  /**
   * The impeded hours, one key per reason, plus the impeded stretches themselves.
   *
   * <p>No aggregate total is written on purpose: the sum of the reasons <em>is</em> the total, and
   * a second accumulator for it is the second truth that lets a breakdown disagree with the figure
   * it explains. The residual of the waiting figure — waiting time with no signal at all — is
   * derived on read for the same reason.
   *
   * <p>{@code impeded_spans} sits beside {@code spans} instead of being cut out of it: an impeded
   * item is still in progress, and punching holes in the intervals that answer "what was in
   * progress?" would quietly answer a different question.
   */
  private void fillImpediment(Map<String, String> detail) {
    impededByReason.forEach(
        (reason, hours) -> {
          detail.put("impeded:" + reason, AdoMapper.num(hours));
          String label = impededLabels.get(reason);
          if (label != null && !label.equals(reason)) {
            detail.put("impeded_label:" + reason, label);
          }
        });
    if (!impededSpans.isEmpty()) {
      detail.put("impeded_spans", impededSpans);
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
      Instant at = RevisionDates.of(u, changed, now);
      if (at == null) {
        // Ilegível, ou vigente sem ChangedDate utilizável: nos dois casos não dá para situar a
        // transição no tempo, e o raw na mensagem é o que permite distinguir um do outro.
        LOG.debug("WI {}: revisão sem data utilizável ({}) — transição descartada", wiId, raw);
        continue;
      }
      if (!at.equals(AdoMapper.parseInstant(raw))) {
        LOG.debug("WI {}: revisão vigente ({}) datada pelo ChangedDate {}", wiId, raw, at);
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
