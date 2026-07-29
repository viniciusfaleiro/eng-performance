package com.engperf.application.ado;

import com.engperf.domain.metrics.EventType;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A read-model snapshot of what the Azure DevOps ingestion has loaded, scoped to a structure node.
 * {@code totals} aggregates the events under the node; {@code rows} breaks them down one level
 * deeper (verticals under {@code all}, teams under a vertical, people under a team), plus buckets
 * for events that don't resolve to that level ("não atribuído", "sem pessoa").
 */
public record AdoStats(
    Instant lastSyncedAt,
    Instant watermark,
    long syncedCount,
    String node,
    String nodeLabel,
    String childType,
    Totals totals,
    List<Row> rows) {

  public AdoStats {
    rows = List.copyOf(rows);
  }

  /** Aggregate counts under the node: total, attribution coverage, per-type and the date span. */
  public record Totals(
      long total,
      long attributed,
      long unattributed,
      Map<EventType, Long> byType,
      Instant firstEventAt,
      Instant lastEventAt) {

    public Totals {
      byType = Map.copyOf(byType);
    }
  }

  /** One breakdown line: a child node (or a residual bucket) with its per-type counts. */
  public record Row(
      String nodeId, String label, String rowType, long total, Map<EventType, Long> byType) {

    public Row {
      byType = Map.copyOf(byType);
    }
  }
}
