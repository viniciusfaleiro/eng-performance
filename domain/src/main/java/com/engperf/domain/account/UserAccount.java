package com.engperf.domain.account;

import com.engperf.domain.common.Text;
import java.util.Objects;

/**
 * A platform login account: email/senha + perfil, optionally linked to a Person (which determines
 * the RBAC scope enforced in S2). The password is stored only as an opaque hash.
 *
 * @param personId linked Person id, or {@code null}
 */
public record UserAccount(
    String id,
    String name,
    String email,
    Role role,
    AccountStatus status,
    String personId,
    String passwordHash) {

  public UserAccount {
    id = Text.required(id, "account id");
    name = Text.required(name, "name");
    email = Text.required(email, "email").toLowerCase(java.util.Locale.ROOT);
    if (!email.contains("@")) {
      throw new IllegalArgumentException("email must contain @");
    }
    Objects.requireNonNull(role, "role must not be null");
    Objects.requireNonNull(status, "status must not be null");
    personId = Text.optional(personId);
    passwordHash = Text.required(passwordHash, "passwordHash");
  }

  public UserAccount withProfile(
      String newName, Role newRole, AccountStatus newStatus, String newPersonId) {
    return new UserAccount(
        id,
        newName == null ? name : newName,
        email,
        newRole == null ? role : newRole,
        newStatus == null ? status : newStatus,
        newPersonId,
        passwordHash);
  }

  public UserAccount withPasswordHash(String newHash) {
    return new UserAccount(id, name, email, role, status, personId, newHash);
  }
}
