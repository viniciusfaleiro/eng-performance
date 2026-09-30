package com.engperf.application.auth;

import com.engperf.application.port.inbound.AuthUseCase;
import com.engperf.application.port.outbound.PasswordHasher;
import com.engperf.application.port.outbound.TokenService;
import com.engperf.application.port.outbound.UserAccountRepositoryPort;
import com.engperf.domain.account.AccountStatus;
import com.engperf.domain.account.UserAccount;
import java.time.Clock;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Authentication service: verifies credentials, issues tokens, changes own password. */
public final class AuthService implements AuthUseCase {

  private static final Logger LOG = LoggerFactory.getLogger(AuthService.class);

  private final UserAccountRepositoryPort accounts;
  private final PasswordHasher passwordHasher;
  private final TokenService tokens;
  private final Clock clock;

  public AuthService(
      UserAccountRepositoryPort accounts,
      PasswordHasher passwordHasher,
      TokenService tokens,
      Clock clock) {
    this.accounts = Objects.requireNonNull(accounts);
    this.passwordHasher = Objects.requireNonNull(passwordHasher);
    this.tokens = Objects.requireNonNull(tokens);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public LoginResult login(String email, String rawPassword) {
    String normalized = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    UserAccount account =
        accounts
            .findByEmail(normalized)
            .orElseThrow(() -> new AuthenticationException("invalid credentials"));
    if (account.status() == AccountStatus.DISABLED) {
      throw new AuthenticationException("account is disabled");
    }
    if (!passwordHasher.matches(rawPassword == null ? "" : rawPassword, account.passwordHash())) {
      throw new AuthenticationException("invalid credentials");
    }
    recordAccess(account);
    AuthPrincipal principal =
        new AuthPrincipal(account.id(), account.email(), account.role(), account.personId());
    return new LoginResult(tokens.issue(principal), principal);
  }

  /**
   * Marks the account as accessed now. Deliberately best-effort: registrar o acesso é efeito
   * colateral do login, não parte dele — perder a entrada de alguém para salvar uma estatística de
   * adoção seria inverter a prioridade.
   */
  private void recordAccess(UserAccount account) {
    try {
      accounts.save(account.withLoginAt(clock.instant()));
    } catch (RuntimeException e) {
      LOG.warn("não foi possível registrar o acesso da conta {}: {}", account.id(), e.getMessage());
    }
  }

  @Override
  public void changePassword(String accountId, String currentPassword, String newPassword) {
    UserAccount account =
        accounts
            .findById(accountId)
            .orElseThrow(() -> new NoSuchElementException("account not found: " + accountId));
    if (!passwordHasher.matches(
        currentPassword == null ? "" : currentPassword, account.passwordHash())) {
      throw new AuthenticationException("current password is incorrect");
    }
    if (newPassword == null || newPassword.isBlank()) {
      throw new IllegalArgumentException("password must not be blank");
    }
    accounts.save(account.withPasswordHash(passwordHasher.hash(newPassword)));
  }
}
