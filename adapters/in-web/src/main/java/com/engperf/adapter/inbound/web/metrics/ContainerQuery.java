package com.engperf.adapter.inbound.web.metrics;

import com.engperf.domain.metrics.ContainerFilter;
import java.util.Arrays;

/**
 * The container selection a metrics endpoint accepts, as {@code ?container=all|containers|leaves}.
 * Absent means everything — the reading that existed before the filter did.
 *
 * <p>An unknown value is rejected with 400 rather than ignored, for the same reason the work-type
 * selection is: a filter that is silently dropped answers a question the reader did not ask, with
 * numbers that look perfectly right.
 */
public record ContainerQuery(String container) {

  /** Spring binds query strings through this; an absent parameter arrives as null. */
  public ContainerQuery() {
    this(null);
  }

  public ContainerFilter filter() {
    if (container == null || container.isBlank()) {
      return ContainerFilter.none();
    }
    return ContainerFilter.byKey(container)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "seleção de contêiner desconhecida: "
                        + container
                        + " — use "
                        + Arrays.stream(ContainerFilter.values())
                            .map(ContainerFilter::key)
                            .toList()));
  }
}
