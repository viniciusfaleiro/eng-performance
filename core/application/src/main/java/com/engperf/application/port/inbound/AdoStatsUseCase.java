package com.engperf.application.port.inbound;

import com.engperf.application.ado.AdoStats;

/**
 * Inbound port: report what the Azure DevOps ingestion has loaded, filtered by a structure node.
 */
public interface AdoStatsUseCase {

  /**
   * Loaded-data statistics under {@code nodeId} ({@code all}, {@code v:…}, {@code t:…} or {@code
   * p:…}); a null/blank node means the whole structure.
   */
  AdoStats stats(String nodeId);
}
