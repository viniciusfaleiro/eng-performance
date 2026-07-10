package com.engperf.application.port.inbound;

import com.engperf.domain.account.AccountStatus;
import com.engperf.domain.account.Role;
import com.engperf.domain.account.UserAccount;
import java.util.List;

/** Inbound port: admin management of platform login accounts (not login/RBAC, which is S2). */
public interface UserAccountUseCase {

  List<UserAccount> accounts();

  UserAccount create(
      String name,
      String email,
      String rawPassword,
      Role role,
      AccountStatus status,
      String personId);

  UserAccount update(String id, String name, Role role, AccountStatus status, String personId);

  void delete(String id);

  void resetPassword(String id, String rawPassword);
}
