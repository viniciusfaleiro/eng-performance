package com.engperf.adapter.inbound.web.admin;

import com.engperf.application.ado.AdoStats;
import com.engperf.domain.metrics.EventType;
import java.util.List;
import java.util.Map;

/** What the Admin → Integração ADO screen shows: loaded-data statistics under a structure node. */
public record AdoStatsDto(
    String lastSyncedAt,
    String watermark,
    long syncedCount,
    String node,
    String nodeLabel,
    String childType,
    Totals totals,
    List<Row> rows) {

  public record Totals(
      long total,
      long attributed,
      long unattributed,
      Map<EventType, Long> byType,
      String firstEventAt,
      String lastEventAt) {}

  public record Row(
      String nodeId, String label, String rowType, long total, Map<EventType, Long> byType) {}

  public static AdoStatsDto from(AdoStats s) {
    AdoStats.Totals t = s.totals();
    Totals totals =
        new Totals(
            t.total(),
            t.attributed(),
            t.unattributed(),
            t.byType(),
            iso(t.firstEventAt()),
            iso(t.lastEventAt()));
    List<Row> rows =
        s.rows().stream()
            .map(r -> new Row(r.nodeId(), r.label(), r.rowType(), r.total(), r.byType()))
            .toList();
    return new AdoStatsDto(
        iso(s.lastSyncedAt()),
        iso(s.watermark()),
        s.syncedCount(),
        s.node(),
        s.nodeLabel(),
        s.childType(),
        totals,
        rows);
  }

  private static String iso(java.time.Instant i) {
    return i == null ? null : i.toString();
  }
}
