package com.engperf.application.port.inbound;

import com.engperf.application.metrics.IndividualDashboard;
import com.engperf.domain.metrics.Frequency;

/**
 * Inbound port for the composed individual (person) contribution panel. Coaching-only access
 * enforcement (only an admin or the managing/own account may view a person) lives in the web
 * adapter; this port composes the panel for an already-authorized person node.
 */
public interface IndividualDashboardUseCase {

  IndividualDashboard dashboard(String personNodeId, Frequency frequency);
}
