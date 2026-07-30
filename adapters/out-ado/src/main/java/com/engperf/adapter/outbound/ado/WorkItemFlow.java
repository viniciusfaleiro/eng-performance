package com.engperf.adapter.outbound.ado;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Function;

/**
 * Flow measures reconstructed from a work item's state-transition history: active/review/wait
 * hours, cycle (first working state → terminal) and lead (creation → completion), the completion
 * instant, and the ACTIVE+REVIEW {@code spans} the individual distribution prorates. Backlog time
 * before the first working state is ignored; wait counts only idle time between the first working
 * and terminal.
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

  static WorkItemFlow of(
      JsonNode updates, Function<String, Segment> classify, Instant created, Instant now) {
    List<StateAt> states = collectStates(updates, classify, now);
    if (states.size() < 2) {
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

  private static List<StateAt> collectStates(
      JsonNode updates, Function<String, Segment> classify, Instant now) {
    List<StateAt> states = new ArrayList<>();
    for (JsonNode u : updates.path("value")) {
      JsonNode sv = u.path("fields").path("System.State");
      if (!sv.hasNonNull("newValue") || !u.hasNonNull("revisedDate")) {
        continue;
      }
      Instant at = AdoMapper.parseInstant(u.path("revisedDate").asText());
      if (at == null || at.isAfter(now)) {
        continue; // unparseable or the "9999" open-revision sentinel
      }
      states.add(new StateAt(at, classify.apply(sv.path("newValue").asText(""))));
    }
    return states;
  }

  private record StateAt(Instant at, Segment segment) {}
}
