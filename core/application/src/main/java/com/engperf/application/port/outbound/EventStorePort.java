package com.engperf.application.port.outbound;

import com.engperf.domain.metrics.EventType;
import com.engperf.domain.metrics.RawEvent;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Outbound port for the raw event store. The seed fills it today; the Azure DevOps adapter (S9)
 * fills the same events later — the engine reads through this port either way.
 */
public interface EventStorePort {

  void saveAll(Collection<RawEvent> events);

  /** Events of a type whose {@code occurredAt} is in [fromInclusive, toExclusive). */
  List<RawEvent> findByTypeBetween(EventType type, Instant fromInclusive, Instant toExclusive);

  /**
   * Every event of a type, with no window.
   *
   * <p>For a metric counted over the interval its events occupy, a window on the event's own date
   * is the wrong question: an item in progress since August carries an August date and is still in
   * progress today, and an item worked in June may carry an August date because that is when it was
   * completed. A trailing window misses the first; no trailing window can contain the second. Only
   * widening it until it holds everything would work, so it is read as everything on purpose,
   * instead of a horizon chosen to look wide enough while still being wrong at the edges.
   *
   * <p>Cost grows with the corpus and not with the period. Narrowing it again is an optimisation
   * that changes no number: the store would pre-filter on the interval's bounds and the exact
   * per-interval test would still run on top.
   */
  List<RawEvent> findByType(EventType type);

  /**
   * The ids of the work-item events that appear as some other work item's parent, over the
   * <strong>whole ingested corpus</strong> — ids, not events.
   *
   * <p>Deliberately not scoped to a period. "Does this item have children?" has to have the same
   * answer whatever window the reader chose: an item whose children ran outside the window would
   * otherwise look like a leaf and start counting time, which is the worst kind of reporting bug —
   * reproducible only for whoever picked the same dates.
   *
   * <p>Returns ids rather than events because the caller only tests membership; loading every work
   * item to derive the same set would cost more as the history grows, for the same answer.
   *
   * <p>An empty set is a valid answer, and is what a store with no parent information gives:
   * nothing has known children, so nothing is excluded, and behaviour falls back to what it was
   * before the relationship was recorded.
   */
  Set<String> parentWorkItemIds();

  long count();
}
