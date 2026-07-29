package com.engperf.application.account;

import com.engperf.application.port.inbound.IdentityUseCase;
import com.engperf.application.port.inbound.UserAccountUseCase;
import com.engperf.application.port.outbound.PasswordHasher;
import com.engperf.application.port.outbound.UserAccountRepositoryPort;
import com.engperf.domain.account.AccountStatus;
import com.engperf.domain.account.Role;
import com.engperf.domain.account.UserAccount;
import com.engperf.domain.common.ConflictException;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Objects;

/** Application service for admin management of login accounts. Framework-free. */
public final class UserAccountService implements UserAccountUseCase {

  private final UserAccountRepositoryPort repository;
  private final PasswordHasher passwordHasher;
  private final IdentityUseCase identities;

  public UserAccountService(
      UserAccountRepositoryPort repository,
      PasswordHasher passwordHasher,
      IdentityUseCase identities) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher must not be null");
    this.identities = Objects.requireNonNull(identities, "identities must not be null");
  }

  @Override
  public List<UserAccount> accounts() {
    return repository.findAll();
  }

  @Override
  public UserAccount create(
      String name,
      String email,
      String rawPassword,
      Role role,
      AccountStatus status,
      String personId) {
    String normalizedEmail = requireEmail(email);
    if (repository.findByEmail(normalizedEmail).isPresent()) {
      throw new ConflictException("email already registered: " + normalizedEmail);
    }
    UserAccount account =
        new UserAccount(
            "u:" + slug(normalizedEmail),
            name,
            normalizedEmail,
            role,
            status == null ? AccountStatus.ACTIVE : status,
            personId,
            hash(rawPassword));
    UserAccount saved = repository.save(account);
    identities.autoLink(); // a new account e-mail may match an existing committer identity
    return saved;
  }

  @Override
  public UserAccount update(
      String id, String name, Role role, AccountStatus status, String personId) {
    UserAccount current = load(id);
    UserAccount saved = repository.save(current.withProfile(name, role, status, personId));
    identities.autoLink(); // a changed person link may now match a committer identity
    return saved;
  }

  @Override
  public void delete(String id) {
    repository.deleteById(id);
  }

  @Override
  public void resetPassword(String id, String rawPassword) {
    UserAccount current = load(id);
    repository.save(current.withPasswordHash(hash(rawPassword)));
  }

  private UserAccount load(String id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new NoSuchElementException("account not found: " + id));
  }

  private String hash(String rawPassword) {
    if (rawPassword == null || rawPassword.isBlank()) {
      throw new IllegalArgumentException("password must not be blank");
    }
    return passwordHasher.hash(rawPassword);
  }

  private static String requireEmail(String email) {
    if (email == null || !email.strip().contains("@")) {
      throw new IllegalArgumentException("valid email required");
    }
    return email.strip().toLowerCase(Locale.ROOT);
  }

  private static String slug(String value) {
    String noAccents = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    return noAccents
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("(^-|-$)", "");
  }
}
