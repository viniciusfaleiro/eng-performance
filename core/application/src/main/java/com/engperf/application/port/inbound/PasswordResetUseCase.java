package com.engperf.application.port.inbound;

/**
 * Inbound port: self-service password reset. Both operations respond the same way regardless of
 * whether the email/token is valid — the neutral response is a requirement, not an implementation
 * detail, so callers must not branch on a distinguishable outcome for {@link #requestReset}.
 */
public interface PasswordResetUseCase {

  /**
   * Issues and emails a reset token if {@code email} belongs to a usable account; always a no-op
   * otherwise.
   */
  void requestReset(String email);

  /**
   * Consumes {@code rawToken} and sets {@code newPassword} as the account's password.
   *
   * @throws com.engperf.application.auth.InvalidResetTokenException if the token is unknown,
   *     expired or already consumed
   * @throws IllegalArgumentException if {@code newPassword} is blank
   */
  void confirmReset(String rawToken, String newPassword);
}
