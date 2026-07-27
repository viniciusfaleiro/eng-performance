package com.engperf.application.port.inbound;

import com.engperf.application.metrics.AiDashboard;
import com.engperf.domain.metrics.Frequency;

/**
 * Inbound port for the composed IA dashboard. Node-scope enforcement (403 outside scope,
 * coaching-only) lives in the web adapter; this port composes values for an already-authorized
 * node.
 */
public interface AiDashboardUseCase {

  AiDashboard dashboard(String nodeId, Frequency frequency);
}
