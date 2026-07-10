package com.engperf.adapter.outbound.persistence;

import com.engperf.domain.account.AccountStatus;
import com.engperf.domain.account.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA mapping for a platform login account. */
@Entity
@Table(name = "user_account")
public class UserAccountEntity {

  @Id private String id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, unique = true)
  private String email;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Role role;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AccountStatus status;

  @Column(name = "person_id")
  private String personId;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  protected UserAccountEntity() {}

  public UserAccountEntity(
      String id,
      String name,
      String email,
      Role role,
      AccountStatus status,
      String personId,
      String passwordHash) {
    this.id = id;
    this.name = name;
    this.email = email;
    this.role = role;
    this.status = status;
    this.personId = personId;
    this.passwordHash = passwordHash;
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

  public Role getRole() {
    return role;
  }

  public AccountStatus getStatus() {
    return status;
  }

  public String getPersonId() {
    return personId;
  }

  public String getPasswordHash() {
    return passwordHash;
  }
}
