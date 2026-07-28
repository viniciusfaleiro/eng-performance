package com.engperf.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class UserAccountTest {

  private static UserAccount account() {
    return new UserAccount(
        "u:ana", "Ana", "Ana@Empresa.com", Role.MANAGER, AccountStatus.ACTIVE, "p:ana", "hash1");
  }

  @Test
  void normalizesEmailAndKeepsFields() {
    UserAccount a = account();
    assertThat(a.email()).isEqualTo("ana@empresa.com");
    assertThat(a.role()).isEqualTo(Role.MANAGER);
    assertThat(a.personId()).isEqualTo("p:ana");
  }

  @Test
  void rejectsBlankAndInvalidEmail() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new UserAccount(
                    "u:x", " ", "a@b.com", Role.ADMIN, AccountStatus.ACTIVE, null, "h"));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new UserAccount("u:x", "X", "no-at", Role.ADMIN, AccountStatus.ACTIVE, null, "h"));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new UserAccount(
                    "u:x", "X", "a@b.com", Role.ADMIN, AccountStatus.ACTIVE, null, " "));
  }

  @Test
  void updatesProfileAndPassword() {
    UserAccount updated =
        account().withProfile("Ana Souza", Role.ADMIN, AccountStatus.DISABLED, null);
    assertThat(updated.name()).isEqualTo("Ana Souza");
    assertThat(updated.role()).isEqualTo(Role.ADMIN);
    assertThat(updated.status()).isEqualTo(AccountStatus.DISABLED);
    assertThat(updated.personId()).isNull();
    assertThat(updated.passwordHash()).isEqualTo("hash1");
    assertThat(account().withPasswordHash("hash2").passwordHash()).isEqualTo("hash2");
  }
}
