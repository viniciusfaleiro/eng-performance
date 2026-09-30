package com.engperf.adapter.inbound.web.admin;

import com.engperf.domain.account.UserAccount;
import java.util.Locale;

/**
 * Request/response DTOs for the user-account admin endpoints (password never leaves the server).
 */
final class AccountDtos {

  private AccountDtos() {}

  record CreateUserRequest(
      String name, String email, String password, String role, String status, String personId) {}

  record UpdateUserRequest(String name, String role, String status, String personId) {}

  record PasswordRequest(String newPassword) {}

  /**
   * @param lastLoginAt quando a conta entrou pela última vez, ou {@code null} para quem nunca
   *     entrou — a tela precisa distinguir os dois, porque "nunca acessou" é o caso que importa
   *     numa implantação, não um dado faltando
   */
  record UserView(
      String id,
      String name,
      String email,
      String role,
      String status,
      String personId,
      String lastLoginAt) {

    static UserView from(UserAccount a) {
      return new UserView(
          a.id(),
          a.name(),
          a.email(),
          a.role().name().toLowerCase(Locale.ROOT),
          a.status().name().toLowerCase(Locale.ROOT),
          a.personId(),
          a.lastLoginAt() == null ? null : a.lastLoginAt().toString());
    }
  }
}
