package com.engperf.application.metrics;

import com.engperf.domain.metrics.InProgressSpans;
import com.engperf.domain.metrics.RawEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * How one person's period broke down by type of work: the slices, the items behind each slice, the
 * container items left out, and the items whose in-progress time is too short to be real work.
 *
 * <p>Only <strong>leaf work</strong> counts. A work item with ingested children is a container of
 * work rather than work itself: leaving it in makes it compete for the same hours as the Tasks it
 * groups, which is how a 30-day window came to report 2.382 hours. Being a container is decided
 * structurally — by having children — and not from a list of types, because a Bug with child Tasks
 * is just as much a container, and a team that works directly in a Feature without opening Tasks
 * would otherwise be reported as idle. An Epic is the one exception decided by type: it is a
 * planning artifact even when empty, and counting an empty one would measure the existence of a
 * plan.
 *
 * @param types the five categories, always all of them, so the legend does not change shape
 * @param containersExcluded how many of the period's items were dropped as containers — what
 *     separates "did no leaf work" from "did nothing"
 * @param shortItems items in progress for less than {@link #SHORT_ITEM_MINUTES} minutes
 */
public record WorkDistribution(
    List<WorkTypeSlice> types, int containersExcluded, List<WorkItemEntry> shortItems) {

  /**
   * Below this, there is no unit of work that deserves a card of its own, so the card was most
   * likely moved after the work was done. Not a number found in the data — a stated judgement, kept
   * as a constant with the reasoning beside it rather than as configuration, because configuration
   * here would need an admin screen for a number nobody will want to tune before there is evidence
   * that 15 is wrong.
   */
  public static final int SHORT_ITEM_MINUTES = 15;

  /** Per list, so that choosing a six-month range cannot turn into a response of megabytes. */
  static final int ITEM_LIMIT = 200;

  private static final double SHORT_ITEM_HOURS = SHORT_ITEM_MINUTES / 60.0;

  public WorkDistribution {
    types = List.copyOf(types);
    shortItems = List.copyOf(shortItems);
  }

  /**
   * @param parentIds the ids of items that are some other item's parent, over the whole corpus —
   *     empty means nothing is known to have children, and then nothing is excluded and the
   *     behaviour is what it was before the relationship was recorded
   */
  public static WorkDistribution of(
      List<RawEvent> items, Set<String> parentIds, Instant from, Instant to, Instant readNow) {
    List<RawEvent> leaves = new ArrayList<>();
    int containers = 0;
    for (RawEvent w : items) {
      if (isContainer(w, parentIds)) {
        containers++;
      } else {
        leaves.add(w);
      }
    }
    Tally tally = tally(leaves, from, to, readNow);
    return new WorkDistribution(slices(tally), containers, shortItems(tally));
  }

  private static boolean isContainer(RawEvent item, Set<String> parentIds) {
    return parentIds.contains(item.id())
        || "epic".equals(item.detail().getOrDefault("ado_type", "").toLowerCase(Locale.ROOT));
  }

  /** What each item was open for and what it ended up contributing, by item and by type. */
  private record Tally(Map<String, Item> byId) {}

  private static final class Item {
    private final RawEvent event;
    private final String type;
    private double elapsed;
    private double counted;

    private Item(RawEvent event, String type) {
      this.event = event;
      this.type = type;
    }

    private WorkItemEntry entry() {
      return new WorkItemEntry(
          event.id(),
          event.detail().getOrDefault("summary", ""),
          event.detail().getOrDefault("url", ""),
          elapsed,
          counted);
    }
  }

  private record Span(long start, long end, String itemId) {}

  private static Tally tally(List<RawEvent> leaves, Instant from, Instant to, Instant readNow) {
    Map<String, Item> byId = new LinkedHashMap<>();
    List<Span> spans = new ArrayList<>();
    for (RawEvent w : leaves) {
      Item item = new Item(w, typeOf(w));
      List<InProgressSpans.Interval> mine = InProgressSpans.of(w, readNow).within(from, to);
      if (mine.isEmpty()) {
        // Eventos antigos, sem histórico de intervalos: a hora total é o que existe. Não é
        // prorrateada nem recortada — é o comportamento que já havia, e mudá-lo aqui misturaria
        // duas correções.
        if (w.detail().containsKey("hours") && !w.detail().containsKey("spans")) {
          item.elapsed = doubleDetail(w, "hours");
          item.counted = item.elapsed;
          byId.put(w.id(), item);
        }
        continue;
      }
      for (InProgressSpans.Interval i : mine) {
        spans.add(new Span(i.start(), i.end(), w.id()));
        item.elapsed += i.hours();
      }
      byId.put(w.id(), item);
    }
    prorate(spans, byId);
    return new Tally(byId);
  }

  /**
   * Splits concurrent wall-clock time among the items in progress at each instant (sweep line), so
   * simultaneous items share the period instead of each counting it in full — the total can never
   * exceed the wall-clock during which the person had at least one item in progress.
   */
  private static void prorate(List<Span> spans, Map<String, Item> byId) {
    TreeSet<Long> marks = new TreeSet<>();
    for (Span s : spans) {
      marks.add(s.start());
      marks.add(s.end());
    }
    Long[] pts = marks.toArray(new Long[0]);
    for (int i = 0; i + 1 < pts.length; i++) {
      long t0 = pts[i];
      long t1 = pts[i + 1];
      List<Span> active = new ArrayList<>();
      for (Span s : spans) {
        if (s.start() <= t0 && s.end() >= t1) {
          active.add(s);
        }
      }
      if (active.isEmpty()) {
        continue;
      }
      double per = (t1 - t0) / (double) active.size() / 3_600_000.0;
      for (Span s : active) {
        byId.get(s.itemId()).counted += per;
      }
    }
  }

  /**
   * The slices, each carrying the items counted under it. The hours of a type are the sum of its
   * items' counted hours and not a separate accumulator, so a list can never fail to add up to the
   * figure it opens from.
   */
  private static List<WorkTypeSlice> slices(Tally tally) {
    Map<String, List<Item>> byType = new LinkedHashMap<>();
    for (Map.Entry<String, String> t : IndividualDashboardService.WORK_TYPES) {
      byType.put(t.getKey(), new ArrayList<>());
    }
    for (Item item : tally.byId().values()) {
      byType.get(item.type).add(item);
    }
    double total =
        tally.byId().values().stream().mapToDouble(i -> i.counted).filter(h -> h > 0).sum();
    List<WorkTypeSlice> slices = new ArrayList<>();
    for (Map.Entry<String, String> t : IndividualDashboardService.WORK_TYPES) {
      List<Item> mine = byType.get(t.getKey());
      mine.sort(Comparator.comparingDouble((Item i) -> i.counted).reversed());
      double hours = mine.stream().mapToDouble(i -> i.counted).sum();
      slices.add(
          new WorkTypeSlice(
              t.getKey(),
              t.getValue(),
              hours,
              total == 0.0 ? 0.0 : hours / total * 100.0,
              mine.size(),
              mine.stream().limit(ITEM_LIMIT).map(Item::entry).toList()));
    }
    return slices;
  }

  /**
   * Short-lived items are judged on elapsed time: the division is our arithmetic, not the board's.
   */
  private static List<WorkItemEntry> shortItems(Tally tally) {
    return tally.byId().values().stream()
        .filter(i -> i.elapsed > 0 && i.elapsed < SHORT_ITEM_HOURS)
        .sorted(Comparator.comparingDouble((Item i) -> i.elapsed))
        .limit(ITEM_LIMIT)
        .map(Item::entry)
        .toList();
  }

  private static String typeOf(RawEvent w) {
    String t = w.detail().getOrDefault("type", "docs");
    boolean known =
        IndividualDashboardService.WORK_TYPES.stream().anyMatch(e -> e.getKey().equals(t));
    return known ? t : "docs";
  }

  private static double doubleDetail(RawEvent e, String key) {
    try {
      return Double.parseDouble(e.detail().getOrDefault(key, "0"));
    } catch (NumberFormatException ex) {
      return 0.0;
    }
  }
}
