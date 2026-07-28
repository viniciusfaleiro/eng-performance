package com.engperf.application.port.inbound;

import com.engperf.application.metrics.FlowDashboard;
import com.engperf.domain.metrics.Frequency;

/**
 * Inbound port for the composed Fluxo dashboard. Node-scope enforcement (403 outside scope,
 * coaching-only) lives in the web adapter; this port computes the dashboard for an authorized node.
 */
public interface FlowDashboardUseCase {

  FlowDashboard dashboard(String nodeId, Frequency frequency);
}
