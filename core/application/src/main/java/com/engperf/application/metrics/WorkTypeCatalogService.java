package com.engperf.application.metrics;

import com.engperf.application.port.inbound.WorkTypeCatalogUseCase;
import com.engperf.application.port.outbound.EventStorePort;
import com.engperf.domain.metrics.EventType;
import com.engperf.domain.metrics.RawEvent;
import com.engperf.domain.metrics.WorkType;
import com.engperf.domain.metrics.WorkTypeMapping;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Builds {@link WorkTypeCatalog} from the declared table plus what the event store holds. */
public final class WorkTypeCatalogService implements WorkTypeCatalogUseCase {

  private final EventStorePort events;

  public WorkTypeCatalogService(EventStorePort events) {
    this.events = Objects.requireNonNull(events, "events must not be null");
  }

  @Override
  public WorkTypeCatalog catalog() {
    List<WorkTypeCatalog.Category> categories =
        WorkType.all().stream().map(t -> new WorkTypeCatalog.Category(t.key(), t.label())).toList();

    List<WorkTypeCatalog.Entry> mapping = new ArrayList<>();
    for (Map.Entry<String, WorkType> e : WorkTypeMapping.table().entrySet()) {
      mapping.add(new WorkTypeCatalog.Entry(e.getKey(), e.getValue().key(), e.getValue().label()));
    }
    mapping.sort(Comparator.comparing(WorkTypeCatalog.Entry::adoType));

    return new WorkTypeCatalog(categories, mapping, unmapped());
  }

  /** The raw types of ingested work items the table does not know, most frequent first. */
  private List<WorkTypeCatalog.UnmappedType> unmapped() {
    Map<String, Long> counts = new TreeMap<>();
    for (RawEvent e : events.findByType(EventType.WORKITEM)) {
      if (!"1".equals(e.detail().get("type_unmapped"))) {
        continue;
      }
      String raw = e.detail().getOrDefault("ado_type", "");
      if (!raw.isBlank()) {
        counts.merge(raw, 1L, Long::sum);
      }
    }
    return counts.entrySet().stream()
        .map(e -> new WorkTypeCatalog.UnmappedType(e.getKey(), e.getValue()))
        .sorted(
            Comparator.comparingLong(WorkTypeCatalog.UnmappedType::count)
                .reversed()
                .thenComparing(WorkTypeCatalog.UnmappedType::adoType))
        .toList();
  }
}
