package com.engperf.domain.structure;

/**
 * Top level of the fixed three-level hierarchy (Vertical → Team → Person).
 *
 * @param managerId the manually assigned manager (a Person id), or {@code null}
 */
public record Vertical(String id, String name, String managerId) {

  public Vertical {
    id = Validation.text(id, "vertical id");
    name = Validation.text(name, "vertical name");
    managerId = Validation.optional(managerId);
  }

  public Vertical withManager(String newManagerId) {
    return new Vertical(id, name, newManagerId);
  }
}
