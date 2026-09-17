package com.engperf.application.port.outbound;

import com.engperf.application.ado.IngestionResult;
import com.engperf.application.ado.ProgressReporter;
import com.engperf.domain.metrics.RawEvent;
import java.time.Instant;

/**
 * Fetches Azure DevOps activity (Repos/PRs/commits, Pipelines, Boards) and maps it to the
 * platform's {@link RawEvent}s. Only activity at or after {@code since} is returned (the
 * watermark); progress is streamed through the reporter. Implemented by the outbound ADO adapter.
 */
public interface AdoEventSourcePort {

  /**
   * Collects everything reachable since {@code since}. A source that cannot be read is reported in
   * the result instead of aborting the run — see {@link IngestionResult}.
   */
  IngestionResult fetchSince(String accessToken, Instant since, ProgressReporter progress);
}
