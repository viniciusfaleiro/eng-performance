package com.engperf.adapter.inbound.web.auth;

import com.engperf.adapter.inbound.web.auth.AuthDtos.ChangePasswordRequest;
import com.engperf.adapter.inbound.web.auth.AuthDtos.LoginRequest;
import com.engperf.adapter.inbound.web.auth.AuthDtos.LoginResponse;
import com.engperf.adapter.inbound.web.auth.AuthDtos.MeView;
import com.engperf.application.auth.AuthenticatedUser;
import com.engperf.application.auth.LoginResult;
import com.engperf.application.port.inbound.AuthUseCase;
import com.engperf.application.port.inbound.AuthorizationUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Inbound REST adapter for platform-native login, session, and self-service password change. */
@RestController
public class AuthController {

  private final AuthUseCase auth;
  private final AuthorizationUseCase authorization;

  public AuthController(AuthUseCase auth, AuthorizationUseCase authorization) {
    this.auth = auth;
    this.authorization = authorization;
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
}
