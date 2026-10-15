package com.engperf.application.port.inbound;

import com.engperf.application.metrics.WorkTypeCatalog;

/**
 * How Azure DevOps work item types are read as the platform's five categories, and which raw types
 * the table does not know. Read-only: the table is a product rule, not configuration.
 */
public interface WorkTypeCatalogUseCase {

  WorkTypeCatalog catalog();
}
