package com.engperf.application.port.inbound;

import com.engperf.application.metrics.AiCard;
import com.engperf.application.metrics.AiDashboard;
import com.engperf.domain.metrics.Frequency;

/**
 * Inbound port for the composed IA dashboard. Node-scope enforcement (403 outside scope,
 * coaching-only) lives in the web adapter; this port composes values for an already-authorized
 * node.
 */
public interface AiDashboardUseCase {

  AiDashboard dashboard(String nodeId, Frequency frequency);

  /**
   * The composed AI-impact card for a node (cycle time of AI vs non-AI PRs), reused by the
   * comparison heatmap so the value matches the IA dashboard's {@code ai_impact} card.
   */
  AiCard impact(String nodeId, Frequency frequency);
}
