package com.engperf.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA mapping for a vertical. Lives in the adapter so the domain stays framework-free. */
@Entity
@Table(name = "vertical")
public class VerticalEntity {

  @Id private String id;

  @Column(nullable = false)
  private String name;

  @Column(name = "manager_id")
  private String managerId;

  protected VerticalEntity() {}

  public VerticalEntity(String id, String name, String managerId) {
    this.id = id;
    this.name = name;
    this.managerId = managerId;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getManagerId() {
    return managerId;
  }
}
