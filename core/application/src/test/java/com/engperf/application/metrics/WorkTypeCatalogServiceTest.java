package com.engperf.application.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.engperf.domain.metrics.EventType;
import com.engperf.domain.metrics.RawEvent;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The Admin read of how Azure DevOps types are understood, and of the ones nobody declared. */
class WorkTypeCatalogServiceTest {

  private final FakeEvents events = new FakeEvents();
  private final WorkTypeCatalogService service = new WorkTypeCatalogService(events);

  @Test
  @DisplayName("o de-para vem da tabela declarada, com as cinco categorias")
  void mappingComesFromTheDeclaredTable() {
    WorkTypeCatalog catalog = service.catalog();

    assertThat(catalog.categories()).extracting(WorkTypeCatalog.Category::key).hasSize(5);
    assertThat(catalog.mapping())
        .anySatisfy(
            e -> {
              assertThat(e.adoType()).isEqualTo("user story");
              assertThat(e.category()).isEqualTo("feature");
            });
  }

  @Test
  @DisplayName("os tipos não declarados saem dos próprios eventos, do mais frequente ao menos")
  void unmappedTypesComeFromTheIngestedEvents() {
    events.add(workItem("w1", "Spike", true));
    events.add(workItem("w2", "Spike", true));
    events.add(workItem("w3", "Impediment", true));
    events.add(workItem("w4", "Bug", false));

    assertThat(service.catalog().unmapped())
        .extracting(WorkTypeCatalog.UnmappedType::adoType, WorkTypeCatalog.UnmappedType::count)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("Spike", 2L),
            org.assertj.core.groups.Tuple.tuple("Impediment", 1L));
  }

  private static RawEvent workItem(String id, String adoType, boolean unmapped) {
    Map<String, String> d =
        unmapped
            ? Map.of("ado_type", adoType, "type_unmapped", "1", "type", "docs")
            : Map.of("ado_type", adoType, "type", "bug");
    return new RawEvent(
        id,
        EventType.WORKITEM,
        Instant.parse("2026-06-15T10:00:00Z"),
        null,
        "id-ana",
        null,
        null,
        false,
        d);
  }
}
