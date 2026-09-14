package com.engperf.application.port.outbound;

import com.engperf.application.ado.DiscoveredRepository;
import java.util.List;

/**
 * Lists the Git repositories of one Azure DevOps (organization, project), each with a best-effort
 * production-stage guess. Implemented by the outbound ADO adapter.
 */
public interface AdoRepositoryDiscoveryPort {

  List<DiscoveredRepository> discover(String accessToken, String organization, String project);
}
