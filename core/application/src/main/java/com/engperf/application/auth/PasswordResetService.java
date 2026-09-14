package com.engperf.application.auth;

import com.engperf.application.port.inbound.PasswordResetUseCase;
import com.engperf.application.port.outbound.EmailSenderPort;
import com.engperf.application.port.outbound.PasswordHasher;
import com.engperf.application.port.outbound.PasswordResetTokenPort;
import com.engperf.application.port.outbound.PlatformConfigPort;
import com.engperf.application.port.outbound.SecureTokenGenerator;
import com.engperf.application.port.outbound.UserAccountRepositoryPort;
import com.engperf.domain.account.AccountStatus;
import com.engperf.domain.account.PasswordResetToken;
import com.engperf.domain.account.UserAccount;
import com.engperf.domain.config.SmtpSettings;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Self-service password reset. Every branch of {@link #requestReset} responds identically to the
 * caller (a no-op or a sent email — never a distinguishable error), so this endpoint cannot be used
 * to enumerate accounts. See {@code openspec/specs/password-reset}.
 */
public final class PasswordResetService implements PasswordResetUseCase {

  static final Duration TOKEN_TTL = Duration.ofMinutes(60);
  static final Duration RATE_WINDOW = Duration.ofMinutes(15);
  static final int RATE_LIMIT = 3;

  private final UserAccountRepositoryPort accounts;
  private final PasswordResetTokenPort tokens;
  private final PasswordHasher passwordHasher;
  private final SecureTokenGenerator tokenGenerator;
  private final EmailSenderPort emailSender;
  private final PlatformConfigPort config;
  private final Clock clock;

  public PasswordResetService(
      UserAccountRepositoryPort accounts,
      PasswordResetTokenPort tokens,
      PasswordHasher passwordHasher,
      SecureTokenGenerator tokenGenerator,
      EmailSenderPort emailSender,
      PlatformConfigPort config,
      Clock clock) {
    this.accounts = Objects.requireNonNull(accounts);
    this.tokens = Objects.requireNonNull(tokens);
    this.passwordHasher = Objects.requireNonNull(passwordHasher);
    this.tokenGenerator = Objects.requireNonNull(tokenGenerator);
    this.emailSender = Objects.requireNonNull(emailSender);
    this.config = Objects.requireNonNull(config);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public void requestReset(String email) {
    String normalized = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    Optional<UserAccount> found = accounts.findByEmail(normalized);
    if (found.isEmpty() || found.get().status() == AccountStatus.DISABLED) {
      return;
    }
    UserAccount account = found.get();

    Instant now = clock.instant();
    if (tokens.countIssuedSince(account.id(), now.minus(RATE_WINDOW)) >= RATE_LIMIT) {
      return;
    }

    tokens.consumeAllPending(account.id(), now);

    String rawToken = tokenGenerator.generate();
    PasswordResetToken token =
        new PasswordResetToken(
            "prt:" + UUID.randomUUID(),
            account.id(),
            tokenGenerator.hash(rawToken),
            now,
            now.plus(TOKEN_TTL),
            null);
    tokens.save(token);

    SmtpSettings settings = config.getSmtpSettings();
    String baseUrl = settings.appBaseUrl() == null ? "" : settings.appBaseUrl();
    String link = baseUrl + "/?reset=" + rawToken;
    emailSender.send(
        account.email(),
        "Eng Performance — redefinição de senha",
        "Recebemos um pedido para redefinir sua senha. Se foi você, acesse o link abaixo em até 1"
            + " hora:\n\n"
            + link
            + "\n\nSe não foi você, ignore esta mensagem.");
  }

  @Override
  public void confirmReset(String rawToken, String newPassword) {
    if (newPassword == null || newPassword.isBlank()) {
      throw new IllegalArgumentException("password must not be blank");
    }
    String tokenHash = tokenGenerator.hash(rawToken == null ? "" : rawToken);
    Instant now = clock.instant();
    PasswordResetToken token =
        tokens
            .findByTokenHash(tokenHash)
            .filter(t -> t.isUsableAt(now))
            .orElseThrow(() -> new InvalidResetTokenException("invalid or expired reset token"));

    UserAccount account =
        accounts
            .findById(token.accountId())
            .orElseThrow(() -> new InvalidResetTokenException("invalid or expired reset token"));

    accounts.save(account.withPasswordHash(passwordHasher.hash(newPassword)));
    tokens.save(token.consumedAt(now));
  }
}
