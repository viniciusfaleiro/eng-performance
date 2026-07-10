package com.engperf.application.port.outbound;

import com.engperf.domain.account.UserAccount;
import java.util.List;
import java.util.Optional;

/** Outbound port: persistence for platform login accounts. */
public interface UserAccountRepositoryPort {

  UserAccount save(UserAccount account);

  List<UserAccount> findAll();

  Optional<UserAccount> findById(String id);

  Optional<UserAccount> findByEmail(String email);

  void deleteById(String id);
}
