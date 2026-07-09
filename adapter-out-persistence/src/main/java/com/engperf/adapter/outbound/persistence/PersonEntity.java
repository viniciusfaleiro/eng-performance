package com.engperf.adapter.outbound.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

/** JPA mapping for a person and its dated memberships (as-of-event). */
@Entity
@Table(name = "person")
public class PersonEntity {

  @Id private String id;

  @Column(nullable = false)
  private String name;

  private String email;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "team_membership", joinColumns = @JoinColumn(name = "person_id"))
  private List<MembershipEmbeddable> memberships = new ArrayList<>();

  protected PersonEntity() {}

  public PersonEntity(
      String id, String name, String email, List<MembershipEmbeddable> memberships) {
    this.id = id;
    this.name = name;
    this.email = email;
    this.memberships = new ArrayList<>(memberships);
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }

  public List<MembershipEmbeddable> getMemberships() {
    return new ArrayList<>(memberships);
  }
}
