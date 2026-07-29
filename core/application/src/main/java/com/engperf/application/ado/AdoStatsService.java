package com.engperf.application.ado;

import com.engperf.application.metrics.StructureIndex;
import com.engperf.application.metrics.StructureIndex.Attribution;
import com.engperf.application.port.inbound.AdoStatsUseCase;
import com.engperf.application.port.outbound.EventStorePort;
import com.engperf.application.port.outbound.StructureRepositoryPort;
import com.engperf.application.port.outbound.SyncStatePort;
import com.engperf.domain.metrics.AttributionScope;
import com.engperf.domain.metrics.EventType;
import com.engperf.domain.metrics.RawEvent;
import com.engperf.domain.structure.Person;
import com.engperf.domain.structure.Team;
import com.engperf.domain.structure.Vertical;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Builds a {@link AdoStats} read model from the event store + structure: attributes every loaded
 * event best-effort (committer identity → person, else repository → team), scopes it to the
 * requested node, and rolls the counts up into totals and a one-level breakdown. Purely a read; it
 * touches no Azure DevOps and mutates nothing.
 */
public final class AdoStatsService implements AdoStatsUseCase {

  // The event store is queried by type over a window; these bounds mean "everything".
  private static final Instant DAWN = Instant.EPOCH;
  private static final Instant HORIZON = Instant.parse("2999-01-01T00:00:00Z");

  private static final String UNATTRIBUTED = "__unattributed";
  private static final String NO_PERSON = "__no_person";

  private final StructureRepositoryPort structure;
  private final EventStorePort events;
  private final SyncStatePort syncState;

  public AdoStatsService(
      StructureRepositoryPort structure, EventStorePort events, SyncStatePort syncState) {
    this.structure = Objects.requireNonNull(structure, "structure must not be null");
    this.events = Objects.requireNonNull(events, "events must not be null");
    this.syncState = Objects.requireNonNull(syncState, "syncState must not be null");
  }

  @Override
  public AdoStats stats(String nodeId) {
    String node = (nodeId == null || nodeId.isBlank()) ? "all" : nodeId;
    StructureIndex index =
        new StructureIndex(
            structure.findPeople(),
            structure.findTeams(),
            structure.findRepositories(),
            structure.findIdentities());
    Map<String, String> vName = names(structure.findVerticals(), Vertical::id, Vertical::name);
    Map<String, String> tName = names(structure.findTeams(), Team::id, Team::name);
    Map<String, String> pName = names(structure.findPeople(), Person::id, Person::name);

    String childType = childType(node);
    long total = 0;
    long attributed = 0;
    long unattributed = 0;
    Map<EventType, Long> byType = zeroCounts();
    Instant first = null;
    Instant last = null;
    Map<String, RowAcc> rows = new LinkedHashMap<>();

    for (RawEvent e : loadAll()) {
      Optional<Attribution> a = attribute(index, e);
      boolean inNode = node.equals("all") || (a.isPresent() && a.get().belongsTo(node));
      if (!inNode) {
        continue;
      }
      total++;
      byType.merge(e.type(), 1L, Long::sum);
      if (a.isPresent()) {
        attributed++;
      } else {
        unattributed++;
      }
      first = earliest(first, e.occurredAt());
      last = latest(last, e.occurredAt());
      accumulateRow(rows, childType, a, e, vName, tName, pName);
    }

    AdoStats.Totals totals =
        new AdoStats.Totals(total, attributed, unattributed, byType, first, last);
    return new AdoStats(
        syncState.load().map(s -> s.lastSyncedAt()).orElse(null),
        syncState.load().map(s -> s.watermark()).orElse(null),
        syncState.load().map(s -> s.eventCount()).orElse(0L),
        node,
        nodeLabel(node, vName, tName, pName),
        childType,
        totals,
        finishRows(rows));
  }

  private void accumulateRow(
      Map<String, RowAcc> rows,
      String childType,
      Optional<Attribution> a,
      RawEvent e,
      Map<String, String> vName,
      Map<String, String> tName,
      Map<String, String> pName) {
    String id;
    String rowType;
    String label;
    switch (childType) {
      case "vertical" -> {
        if (a.isPresent()) {
          id = a.get().verticalId();
          rowType = "vertical";
          label = vName.getOrDefault(id, id);
        } else {
          id = UNATTRIBUTED;
          rowType = "unattributed";
          label = "Não atribuído";
        }
      }
      case "team" -> {
        id = a.get().teamId();
        rowType = "team";
        label = tName.getOrDefault(id, id);
      }
      case "person" -> {
        if (a.get().personId() != null) {
          id = a.get().personId();
          rowType = "person";
          label = pName.getOrDefault(id, id);
        } else {
          id = NO_PERSON;
          rowType = "repo";
          label = "— sem pessoa (deploys/repo)";
        }
      }
      default -> {
        return; // person-level node has no children
      }
    }
    rows.computeIfAbsent(id, k -> new RowAcc(k, label, rowType)).add(e.type());
  }

  private List<RawEvent> loadAll() {
    List<RawEvent> all = new ArrayList<>();
    for (EventType type : EventType.values()) {
      all.addAll(events.findByTypeBetween(type, DAWN, HORIZON));
    }
    return all;
  }

  /** Committer identity → person first; failing that, repository → team; else unattributed. */
  private static Optional<Attribution> attribute(StructureIndex index, RawEvent e) {
    Optional<Attribution> a = Optional.empty();
    if (e.committerIdentity() != null) {
      a = index.attribute(e, AttributionScope.PERSON);
    }
    if (a.isEmpty() && e.repoKey() != null) {
      a = index.attribute(e, AttributionScope.REPO);
    }
    return a;
  }

  private static List<AdoStats.Row> finishRows(Map<String, RowAcc> rows) {
    return rows.values().stream()
        .sorted(
            Comparator.comparing(RowAcc::isResidual)
                .thenComparing(Comparator.comparingLong(RowAcc::total).reversed()))
        .map(RowAcc::toRow)
        .toList();
  }

  private static String childType(String node) {
    if (node.equals("all")) {
      return "vertical";
    }
    if (node.startsWith("v:")) {
      return "team";
    }
    if (node.startsWith("t:")) {
      return "person";
    }
    return "none";
  }

  private static String nodeLabel(
      String node,
      Map<String, String> vName,
      Map<String, String> tName,
      Map<String, String> pName) {
    if (node.equals("all")) {
      return "Toda a estrutura";
    }
    if (node.startsWith("v:")) {
      return vName.getOrDefault(node, node);
    }
    if (node.startsWith("t:")) {
      return tName.getOrDefault(node, node);
    }
    return pName.getOrDefault(node, node);
  }

  private static <T> Map<String, String> names(
      List<T> items,
      java.util.function.Function<T, String> id,
      java.util.function.Function<T, String> name) {
    Map<String, String> out = new LinkedHashMap<>();
    items.forEach(i -> out.put(id.apply(i), name.apply(i)));
    return out;
  }

  private static Map<EventType, Long> zeroCounts() {
    Map<EventType, Long> m = new EnumMap<>(EventType.class);
    for (EventType t : EventType.values()) {
      m.put(t, 0L);
    }
    return m;
  }

  private static Instant earliest(Instant current, Instant candidate) {
    return current == null || candidate.isBefore(current) ? candidate : current;
  }

  private static Instant latest(Instant current, Instant candidate) {
    return current == null || candidate.isAfter(current) ? candidate : current;
  }

  /** Mutable per-row accumulator, finished into an immutable {@link AdoStats.Row}. */
  private static final class RowAcc {
    private final String id;
    private final String label;
    private final String rowType;
    private final Map<EventType, Long> byType = zeroCounts();
    private long total;

    RowAcc(String id, String label, String rowType) {
      this.id = id;
      this.label = label;
      this.rowType = rowType;
    }

    void add(EventType type) {
      total++;
      byType.merge(type, 1L, Long::sum);
    }

    long total() {
      return total;
    }

    boolean isResidual() {
      return rowType.equals("unattributed") || rowType.equals("repo");
    }

    AdoStats.Row toRow() {
      String nodeId = id.startsWith("__") ? null : id;
      return new AdoStats.Row(nodeId, label, rowType, total, byType);
    }
  }
}
