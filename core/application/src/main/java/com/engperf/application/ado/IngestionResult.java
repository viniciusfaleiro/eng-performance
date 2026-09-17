package com.engperf.application.ado;

import com.engperf.domain.metrics.RawEvent;
import java.util.List;

/**
 * What one ingestion run produced: the events it collected and the sources it could not read.
 *
 * <p>A partial collection is a normal outcome, not an exception — registered repositories and the
 * reality of Azure DevOps drift apart all the time (renames, removals, permissions). Modelling it
 * as a return value is what lets the caller ingest what worked and still decide what to do about
 * the rest; an exception would force the old all-or-nothing choice.
 */
public record IngestionResult(List<RawEvent> events, List<SourceFailure> failures) {

  public IngestionResult {
    events = List.copyOf(events);
    failures = List.copyOf(failures);
  }

  public boolean isPartial() {
    return !failures.isEmpty();
  }
}
