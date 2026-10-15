package com.engperf.adapter.inbound.web.metrics;

import com.engperf.domain.metrics.WorkType;
import com.engperf.domain.metrics.WorkTypeFilter;
import java.util.ArrayList;
import java.util.List;

/**
 * The work-type selection every metrics endpoint accepts, as a repeatable {@code workType}
 * parameter ({@code ?workType=bug&workType=tech_debt}). Absent means no selection — everything.
 *
 * <p>An unknown category is rejected with 400 rather than ignored. An ignored filter answers a
 * question the reader did not ask, with numbers that look perfectly right.
 */
public record WorkTypeQuery(List<String> workType) {

  /** Spring binds query strings through this; an absent parameter arrives as null. */
  public WorkTypeQuery() {
    this(null);
  }

  public WorkTypeFilter filter() {
    if (workType == null || workType.isEmpty()) {
      return WorkTypeFilter.none();
    }
    List<WorkType> selected = new ArrayList<>();
    for (String raw : workType) {
      if (raw == null || raw.isBlank()) {
        continue;
      }
      selected.add(
          WorkType.byKey(raw)
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "tipo de trabalho desconhecido: "
                              + raw
                              + " — use "
                              + WorkType.all().stream().map(WorkType::key).toList())));
    }
    return WorkTypeFilter.of(selected);
  }
}
