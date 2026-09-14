package com.engperf.adapter.inbound.web.auth;

import com.engperf.adapter.inbound.web.auth.AuthDtos.ChangePasswordRequest;
import com.engperf.adapter.inbound.web.auth.AuthDtos.LoginRequest;
import com.engperf.adapter.inbound.web.auth.AuthDtos.LoginResponse;
import com.engperf.adapter.inbound.web.auth.AuthDtos.MeView;
import com.engperf.adapter.inbound.web.auth.AuthDtos.PasswordResetConfirmRequest;
import com.engperf.adapter.inbound.web.auth.AuthDtos.PasswordResetRequest;
import com.engperf.application.auth.AuthenticatedUser;
import com.engperf.application.auth.LoginResult;
import com.engperf.application.port.inbound.AuthUseCase;
import com.engperf.application.port.inbound.AuthorizationUseCase;
import com.engperf.application.port.inbound.PasswordResetUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound REST adapter for platform-native login, session, self-service password change, and
 * self-service password reset by emailed token.
 */
@RestController
public class AuthController {

  private final AuthUseCase auth;
  private final AuthorizationUseCase authorization;
  private final PasswordResetUseCase passwordReset;

  public AuthController(
      AuthUseCase auth, AuthorizationUseCase authorization, PasswordResetUseCase passwordReset) {
    this.auth = auth;
    this.authorization = authorization;
    this.passwordReset = passwordReset;
  }

  @PostMapping("/api/auth/login")
  public LoginResponse login(@RequestBody LoginRequest request) {
    LoginResult result = auth.login(request.email(), request.password());
    AuthenticatedUser user = authorization.currentUser(result.principal());
    return LoginResponse.of(result, user);
  }

  /** Stateless (JWT): logout is client-side (drop the token). Endpoint exists for symmetry. */
  @PostMapping("/api/auth/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout() {}

  @GetMapping("/api/auth/me")
  public MeView me(@RequestAttribute(AuthWeb.USER) AuthenticatedUser user) {
    return MeView.from(user);
  }

  @PutMapping("/api/auth/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void changePassword(
      @RequestAttribute(AuthWeb.USER) AuthenticatedUser user,
      @RequestBody ChangePasswordRequest request) {
    auth.changePassword(user.account().id(), request.currentPassword(), request.newPassword());
  }

  /**
   * Always 202, regardless of whether {@code email} belongs to an account — see {@code
   * openspec/specs/password-reset} (the response must never reveal which emails exist).
   */
  @PostMapping("/api/auth/password-reset")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void requestPasswordReset(@RequestBody PasswordResetRequest request) {
    passwordReset.requestReset(request.email());
  }

  @PostMapping("/api/auth/password-reset/confirm")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void confirmPasswordReset(@RequestBody PasswordResetConfirmRequest request) {
    passwordReset.confirmReset(request.token(), request.newPassword());
  }
}
