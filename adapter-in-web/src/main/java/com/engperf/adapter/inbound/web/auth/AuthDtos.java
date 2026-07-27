package com.engperf.adapter.inbound.web.auth;

import com.engperf.application.auth.AuthenticatedUser;
import com.engperf.application.auth.LoginResult;
import java.util.Locale;

/** Request/response payloads for the authentication endpoints. */
public final class AuthDtos {

  private AuthDtos() {}

  public record LoginRequest(String email, String password) {}

  public record ChangePasswordRequest(String currentPassword, String newPassword) {}

  /** The authenticated identity plus the coarse scope flags the frontend uses to render. */
  public record MeView(
      String accountId,
      String name,
      String email,
      String role,
      String personId,
      boolean admin,
      boolean orgWide) {

    public static MeView from(AuthenticatedUser user) {
      return new MeView(
          user.account().id(),
          user.account().name(),
          user.account().email(),
          user.account().role().name().toLowerCase(Locale.ROOT),
          user.account().personId(),
          user.scope().canConfigure(),
          user.scope().orgWide());
    }
  }

  public record LoginResponse(String token, MeView user) {

    public static LoginResponse of(LoginResult result, AuthenticatedUser user) {
      return new LoginResponse(result.token(), MeView.from(user));
    }
  }
}
