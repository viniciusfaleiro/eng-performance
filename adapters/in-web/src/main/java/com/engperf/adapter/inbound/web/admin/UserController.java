package com.engperf.adapter.inbound.web.admin;

import com.engperf.adapter.inbound.web.admin.AccountDtos.CreateUserRequest;
import com.engperf.adapter.inbound.web.admin.AccountDtos.PasswordRequest;
import com.engperf.adapter.inbound.web.admin.AccountDtos.UpdateUserRequest;
import com.engperf.adapter.inbound.web.admin.AccountDtos.UserView;
import com.engperf.application.port.inbound.UserAccountUseCase;
import com.engperf.domain.account.AccountStatus;
import com.engperf.domain.account.Role;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Inbound REST adapter for admin management of login accounts. */
@RestController
public class UserController {

  private final UserAccountUseCase users;

  public UserController(UserAccountUseCase users) {
    this.users = users;
  }

  @GetMapping("/api/admin/users")
  public List<UserView> list() {
    return users.accounts().stream().map(UserView::from).toList();
  }

  @PostMapping("/api/admin/users")
  @ResponseStatus(HttpStatus.CREATED)
  public UserView create(@RequestBody CreateUserRequest request) {
    return UserView.from(
        users.create(
            request.name(),
            request.email(),
            request.password(),
            role(request.role()),
            status(request.status()),
            request.personId()));
  }

  @PutMapping("/api/admin/users/{id}")
  public UserView update(@PathVariable String id, @RequestBody UpdateUserRequest request) {
    return UserView.from(
        users.update(
            id,
            request.name(),
            role(request.role()),
            status(request.status()),
            request.personId()));
  }

  @DeleteMapping("/api/admin/users/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String id) {
    users.delete(id);
  }

  @PutMapping("/api/admin/users/{id}/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void resetPassword(@PathVariable String id, @RequestBody PasswordRequest request) {
    users.resetPassword(id, request.newPassword());
  }

  private static Role role(String value) {
    return value == null ? null : parse(Role.class, value, "role");
  }

  private static AccountStatus status(String value) {
    return value == null ? null : parse(AccountStatus.class, value, "status");
  }

  private static <E extends Enum<E>> E parse(Class<E> type, String value, String field) {
    try {
      return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("invalid " + field + ": " + value);
    }
  }
}
