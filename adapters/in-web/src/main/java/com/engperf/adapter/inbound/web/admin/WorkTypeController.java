package com.engperf.adapter.inbound.web.admin;

import com.engperf.application.metrics.WorkTypeCatalog;
import com.engperf.application.port.inbound.WorkTypeCatalogUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * How Azure DevOps work item types are read as the platform's five categories, for the Admin
 * screen: the declared table and the raw types the ingestion met and did not recognise. Read-only —
 * the table is a product rule, not something an administrator tunes.
 */
@RestController
public class WorkTypeController {

  private final WorkTypeCatalogUseCase workTypes;

  public WorkTypeController(WorkTypeCatalogUseCase workTypes) {
    this.workTypes = workTypes;
  }

  @GetMapping("/api/admin/work-types")
  public WorkTypeCatalog catalog() {
    return workTypes.catalog();
  }
}
