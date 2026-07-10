package com.engperf.adapter.outbound.persistence;

import com.engperf.application.port.outbound.UserAccountRepositoryPort;
import com.engperf.domain.account.UserAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL-backed adapter for {@link UserAccountRepositoryPort}. */
@Component
@Transactional
public class JpaUserAccountRepository implements UserAccountRepositoryPort {

  private final UserAccountJpaRepository accounts;

  public JpaUserAccountRepository(UserAccountJpaRepository accounts) {
    this.accounts = accounts;
  }

  @Override
  public UserAccount save(UserAccount account) {
    accounts.save(
        new UserAccountEntity(
            account.id(),
            account.name(),
            account.email(),
            account.role(),
            account.status(),
            account.personId(),
            account.passwordHash()));
    return account;
  }

  @Override
  @Transactional(readOnly = true)
  public List<UserAccount> findAll() {
    return accounts.findAll().stream().map(JpaUserAccountRepository::toDomain).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UserAccount> findById(String id) {
    return accounts.findById(id).map(JpaUserAccountRepository::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UserAccount> findByEmail(String email) {
    return accounts.findByEmail(email).map(JpaUserAccountRepository::toDomain);
  }

  @Override
  public void deleteById(String id) {
    accounts.deleteById(id);
  }

  private static UserAccount toDomain(UserAccountEntity e) {
    return new UserAccount(
        e.getId(),
        e.getName(),
        e.getEmail(),
        e.getRole(),
        e.getStatus(),
        e.getPersonId(),
        e.getPasswordHash());
  }
}
