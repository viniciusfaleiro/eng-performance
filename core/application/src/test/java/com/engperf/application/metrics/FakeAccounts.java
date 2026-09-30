package com.engperf.application.metrics;

import com.engperf.application.port.outbound.UserAccountRepositoryPort;
import com.engperf.domain.account.UserAccount;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Contas ligadas a pessoas — o painel só precisa saber se existe e quando acessou. */
final class FakeAccounts implements UserAccountRepositoryPort {
  final List<UserAccount> all = new ArrayList<>();

  @Override
  public List<UserAccount> findAll() {
    return all;
  }

  @Override
  public UserAccount save(UserAccount account) {
    all.add(account);
    return account;
  }

  @Override
  public Optional<UserAccount> findById(String id) {
    return all.stream().filter(a -> a.id().equals(id)).findFirst();
  }

  @Override
  public Optional<UserAccount> findByEmail(String email) {
    return all.stream().filter(a -> a.email().equals(email)).findFirst();
  }

  @Override
  public void deleteById(String id) {
    all.removeIf(a -> a.id().equals(id));
  }
}
