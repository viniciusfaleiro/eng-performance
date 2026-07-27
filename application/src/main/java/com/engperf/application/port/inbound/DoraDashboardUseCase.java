package com.engperf.application.port.inbound;

import com.engperf.application.metrics.DoraDashboard;
import com.engperf.domain.metrics.Frequency;

/**
 * Inbound port for the composed DORA dashboard. Node-scope enforcement (403 outside scope,
 * coaching-only) lives in the web adapter; this port computes the dashboard for an authorized node.
 */
public interface DoraDashboardUseCase {

  DoraDashboard dashboard(String nodeId, Frequency frequency);
}
