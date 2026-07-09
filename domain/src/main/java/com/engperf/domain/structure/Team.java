package com.engperf.domain.structure;

/**
 * Middle level of the hierarchy. Belongs to exactly one {@link Vertical}.
 *
 * @param managerId manually assigned manager (a Person id), or {@code null}
 * @param productionStageOverride overrides the global production-stage rule, or {@code null}
 */
public record Team(
    String id, String name, String verticalId, String managerId, String productionStageOverride) {

  public Team {
    id = Validation.text(id, "team id");
    name = Validation.text(name, "team name");
    verticalId = Validation.text(verticalId, "vertical id");
    managerId = Validation.optional(managerId);
    productionStageOverride = Validation.optional(productionStageOverride);
  }

  public Team withManager(String newManagerId) {
    return new Team(id, name, verticalId, newManagerId, productionStageOverride);
  }
}
