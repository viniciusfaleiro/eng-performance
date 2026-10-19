package com.engperf.adapter.outbound.ado;

import com.engperf.domain.config.ImpedimentConvention;
import com.engperf.domain.metrics.Interval;
import com.engperf.domain.metrics.Intervals;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The stretches of time a work item was impeded, and the reason the team gave for each.
 *
 * <p>The board this was built for has no blocked column: impediment is a field being set and/or a
 * tag being added, both configured per organisation. So the signal cannot come from the state
 * history alone — but it comes from the <em>same</em> payload. The update history is already
 * fetched once per work item for the state transitions, and it carries the before/after of every
 * field and of the tags, which is why reading the signal costs no request of its own.
 *
 * <p>The two signals are a union, not a choice: a team that sets the field and tags the reason is
 * not impeded twice. The reason comes only from the tag — the field says <em>that</em> something
 * blocked, the tag says <em>what</em> — and impeded time with no reason goes to a reserved bucket
 * rather than being dropped, because time nobody labelled is still time the work stood still.
 */
final class ImpedimentTimeline {

  private static final Logger LOG = LoggerFactory.getLogger(ImpedimentTimeline.class);

  /** The bucket for impeded time nobody gave a reason for; no tag can normalise to it. */
  static final String NO_REASON = "?";

  private static final String TAGS = "System.Tags";
  private static final ImpedimentTimeline EMPTY =
      new ImpedimentTimeline(List.of(), Map.of(), Map.of());

  private final List<Interval> blocked;
  private final Map<String, List<Interval>> byReason;
  private final Map<String, String> labels;

  private ImpedimentTimeline(
      List<Interval> blocked, Map<String, List<Interval>> byReason, Map<String, String> labels) {
    this.blocked = blocked;
    this.byReason = byReason;
    this.labels = labels;
  }

  static ImpedimentTimeline none() {
    return EMPTY;
  }

  /**
   * Reconstructs the timeline from the item's update history.
   *
   * <p>The state before the first recorded change is taken from that change's {@code oldValue}, so
   * an item tagged at creation is impeded from the start of its history rather than from the moment
   * someone happened to edit the tag. A signal still in force on the last revision runs to {@code
   * now} — the same open end the state reconstruction gives the item's current state.
   */
  static ImpedimentTimeline of(
      String wiId,
      JsonNode updates,
      ImpedimentConvention convention,
      Instant changed,
      Instant now) {
    if (convention == null || !convention.enabled()) {
      return EMPTY;
    }
    List<Change> changes = changes(wiId, updates, convention, changed, now);
    if (changes.isEmpty()) {
      return EMPTY;
    }
    List<Interval> blockedRaw = new ArrayList<>();
    Map<String, List<Interval>> reasonRaw = new LinkedHashMap<>();
    Map<String, String> labels = new LinkedHashMap<>();

    boolean fieldOn = seedField(changes);
    Set<String> reasons = seedReasons(changes, labels);

    long cursor = changes.get(0).at();
    for (Change c : changes) {
      if (c.at() > cursor) {
        record(blockedRaw, reasonRaw, cursor, c.at(), fieldOn, reasons);
      }
      if (c.fieldOn() != null) {
        fieldOn = c.fieldOn();
      }
      if (c.reasons() != null) {
        reasons = c.reasons();
        reasons.forEach(r -> putLabel(labels, r, c.labels()));
      }
      cursor = Math.max(cursor, c.at());
    }
    record(blockedRaw, reasonRaw, cursor, now.toEpochMilli(), fieldOn, reasons);

    if (blockedRaw.isEmpty()) {
      return EMPTY;
    }
    Map<String, List<Interval>> byReason = new LinkedHashMap<>();
    reasonRaw.forEach((reason, raw) -> byReason.put(reason, Intervals.union(raw)));
    ImpedimentTimeline timeline =
        new ImpedimentTimeline(Intervals.union(blockedRaw), byReason, Map.copyOf(labels));
    LOG.debug(
        "WI {}: {} intervalo(s) impedido(s), motivos {}",
        wiId,
        timeline.blocked.size(),
        byReason.keySet());
    return timeline;
  }

  /** The reserved bucket has no spelling of its own, so it never reaches the label index. */
  private static void putLabel(
      Map<String, String> labels, String reason, Map<String, String> seen) {
    String label = seen.get(reason);
    if (label != null) {
      labels.putIfAbsent(reason, label);
    }
  }

  private static void record(
      List<Interval> blockedRaw,
      Map<String, List<Interval>> reasonRaw,
      long from,
      long to,
      boolean fieldOn,
      Set<String> reasons) {
    if (to <= from || (!fieldOn && reasons.isEmpty())) {
      return;
    }
    Interval interval = new Interval(from, to);
    blockedRaw.add(interval);
    for (String reason : reasons) {
      reasonRaw.computeIfAbsent(reason, r -> new ArrayList<>()).add(interval);
    }
  }

  boolean isEmpty() {
    return blocked.isEmpty();
  }

  /** How much of {@code [from, to)} the item spent impeded, in milliseconds. */
  long overlapMs(Instant from, Instant to) {
    return Intervals.overlapMs(blocked, from.toEpochMilli(), to.toEpochMilli());
  }

  /** The impeded stretches themselves, for the readings that subtract them from working time. */
  List<Interval> blocked() {
    return blocked;
  }

  /**
   * Impeded hours by reason inside {@code windows} — the stretches of the item's life the flow
   * actually counted. Clipping to them is what keeps the breakdown from claiming more than the
   * waiting figure it explains: time impeded in the backlog, before any work started, is in
   * neither.
   *
   * <p>The leftover — impeded time inside the windows that no tag accounted for — lands on {@link
   * #NO_REASON}.
   */
  Map<String, Double> hoursByReason(List<Interval> windows) {
    if (blocked.isEmpty() || windows.isEmpty()) {
      return Map.of();
    }
    List<Interval> inside = Intervals.union(clipAll(blocked, windows));
    if (inside.isEmpty()) {
      return Map.of();
    }
    Map<String, Double> out = new LinkedHashMap<>();
    List<Interval> accounted = new ArrayList<>();
    double unnamed = 0;
    for (Map.Entry<String, List<Interval>> e : byReason.entrySet()) {
      List<Interval> clipped = Intervals.union(clipAll(e.getValue(), windows));
      double hours = hours(clipped);
      if (hours <= 0) {
        continue;
      }
      accounted.addAll(clipped);
      if (NO_REASON.equals(e.getKey())) {
        unnamed += hours;
      } else {
        out.put(e.getKey(), hours);
      }
    }
    // O que sobrou é impedimento sem tag nenhuma — o campo marcado, tipicamente — e vai para o
    // mesmo bucket da tag sem motivo: as duas dizem "travou" sem dizer no quê. Sempre por último,
    // para que a quebra leia primeiro o que o time nomeou.
    double unaccounted = unnamed + hours(Intervals.subtract(inside, accounted));
    if (unaccounted > 0) {
      out.put(NO_REASON, unaccounted);
    }
    return out;
  }

  /** The spelling the team used for a reason, for the legend. */
  String label(String reason) {
    return labels.getOrDefault(reason, reason);
  }

  private static List<Interval> clipAll(List<Interval> intervals, List<Interval> windows) {
    List<Interval> out = new ArrayList<>();
    for (Interval w : windows) {
      out.addAll(Intervals.clip(intervals, w.start(), w.end()));
    }
    return out;
  }

  private static double hours(List<Interval> intervals) {
    double total = 0;
    for (Interval i : intervals) {
      total += i.hours();
    }
    return total;
  }

  // ---- reading the update history ----

  /** One recorded change; {@code null} in a component means "this update did not touch it". */
  private record Change(
      long at,
      Boolean fieldOn,
      boolean fieldWasOn,
      Set<String> reasons,
      Set<String> reasonsBefore,
      Map<String, String> labels) {}

  private static List<Change> changes(
      String wiId,
      JsonNode updates,
      ImpedimentConvention convention,
      Instant changed,
      Instant now) {
    List<Change> out = new ArrayList<>();
    for (JsonNode u : updates.path("value")) {
      JsonNode field =
          convention.fieldName() == null
              ? null
              : nonEmpty(u.path("fields").path(convention.fieldName()));
      JsonNode tags = convention.tagPrefix() == null ? null : nonEmpty(u.path("fields").path(TAGS));
      if (field == null && tags == null) {
        continue;
      }
      Instant at = RevisionDates.of(u, changed, now);
      if (at == null) {
        LOG.debug(
            "WI {}: revisão sem data utilizável ({}) — mudança de impedimento descartada",
            wiId,
            u.path("revisedDate").asText(""));
        continue;
      }
      Map<String, String> labels = new LinkedHashMap<>();
      out.add(
          new Change(
              at.toEpochMilli(),
              field == null ? null : set(field.path("newValue").asText("")),
              field != null && set(field.path("oldValue").asText("")),
              tags == null ? null : reasons(tags.path("newValue").asText(""), convention, labels),
              tags == null
                  ? Set.of()
                  : reasons(tags.path("oldValue").asText(""), convention, labels),
              labels));
    }
    out.sort(Comparator.comparingLong(Change::at));
    return out;
  }

  private static JsonNode nonEmpty(JsonNode node) {
    return node.isMissingNode() || node.isNull() ? null : node;
  }

  /**
   * The field's value before the first change that touched it. An item whose field was already set
   * when its history starts was impeded before anyone edited it, and reading only {@code newValue}
   * would date that impediment to the moment it was <em>cleared</em>.
   */
  private static boolean seedField(List<Change> changes) {
    for (Change c : changes) {
      if (c.fieldOn() != null) {
        return c.fieldWasOn();
      }
    }
    return false;
  }

  /** Likewise for the tags: the reasons in force before the first tag change. */
  private static Set<String> seedReasons(List<Change> changes, Map<String, String> labels) {
    for (Change c : changes) {
      if (c.reasons() != null) {
        c.reasonsBefore().forEach(r -> putLabel(labels, r, c.labels()));
        return new LinkedHashSet<>(c.reasonsBefore());
      }
    }
    return new LinkedHashSet<>();
  }

  /** Whether the field's value means "impeded" — anything but blank and the board's "None". */
  static boolean set(String value) {
    String v = value == null ? "" : value.strip();
    return !v.isEmpty() && !"none".equals(v.toLowerCase(Locale.ROOT));
  }

  /**
   * The reasons carried by a tag list. Tags arrive semicolon-separated; a tag that starts with the
   * configured prefix yields the text after it as the reason, normalised so the same reason written
   * differently is one reason.
   */
  static Set<String> reasons(
      String tagList, ImpedimentConvention convention, Map<String, String> labels) {
    Set<String> out = new LinkedHashSet<>();
    String prefix = convention.tagPrefix();
    if (prefix == null || tagList == null || tagList.isBlank()) {
      return out;
    }
    String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
    for (String raw : tagList.split(";")) {
      String tag = raw.strip();
      if (tag.length() < prefix.length() || !tag.toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) {
        continue;
      }
      String label = tag.substring(prefix.length()).strip();
      String key = normalise(label);
      if (key.isEmpty()) {
        // Tag só com o prefixo: sinaliza que algo travou sem dizer o quê. O tempo conta, no bucket
        // reservado — descartá-lo esconderia impedimento de quem o registrou, só não nomeou.
        out.add(NO_REASON);
        continue;
      }
      out.add(key);
      labels.putIfAbsent(key, label);
    }
    return out;
  }

  /**
   * The reason's stable key: case and spacing ignored, and the three characters that would make it
   * ambiguous as a detail key or collide with {@link #NO_REASON} replaced.
   */
  private static String normalise(String label) {
    return label.strip().toLowerCase(Locale.ROOT).replaceAll("[,:?]", "-");
  }
}
