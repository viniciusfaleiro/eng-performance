package com.engperf.application.port.outbound;

import com.engperf.application.ado.ProgressReporter;
import com.engperf.domain.metrics.RawEvent;
import java.time.Instant;
import java.util.List;

/**
 * Fetches Azure DevOps activity (Repos/PRs/commits, Pipelines, Boards) and maps it to the
 * platform's {@link RawEvent}s. Only activity at or after {@code since} is returned (the
 * watermark); progress is streamed through the reporter. Implemented by the outbound ADO adapter.
 */
public interface AdoEventSourcePort {

  List<RawEvent> fetchSince(String accessToken, Instant since, ProgressReporter progress);
}
