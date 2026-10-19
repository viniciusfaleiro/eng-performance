package com.engperf.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA mapping for the singleton impediment convention (id = "default"). */
@Entity
@Table(name = "impediment_convention")
public class ImpedimentConventionEntity {

  @Id private String id;

  @Column(name = "field_name")
  private String fieldName;

  @Column(name = "tag_prefix")
  private String tagPrefix;

  protected ImpedimentConventionEntity() {}

  public ImpedimentConventionEntity(String id, String fieldName, String tagPrefix) {
    this.id = id;
    this.fieldName = fieldName;
    this.tagPrefix = tagPrefix;
  }

  public String getFieldName() {
    return fieldName;
  }

  public String getTagPrefix() {
    return tagPrefix;
  }
}
