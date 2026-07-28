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

  record UserView(
      String id, String name, String email, String role, String status, String personId) {

    static UserView from(UserAccount a) {
      return new UserView(
          a.id(),
          a.name(),
          a.email(),
          a.role().name().toLowerCase(Locale.ROOT),
          a.status().name().toLowerCase(Locale.ROOT),
          a.personId());
    }
  }
}
