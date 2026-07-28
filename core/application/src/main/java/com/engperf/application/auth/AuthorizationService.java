package com.engperf.application.auth;

import com.engperf.application.port.inbound.AuthorizationUseCase;
import com.engperf.application.port.outbound.StructureRepositoryPort;
import com.engperf.application.port.outbound.UserAccountRepositoryPort;
import com.engperf.domain.access.AccessPolicy;
import com.engperf.domain.access.AccessScope;
import com.engperf.domain.account.UserAccount;
import java.util.Objects;

/** Resolves an authenticated principal's access scope from the current structure. */
public final class AuthorizationService implements AuthorizationUseCase {

  private final UserAccountRepositoryPort accounts;
  private final StructureRepositoryPort structure;

  public AuthorizationService(
      UserAccountRepositoryPort accounts, StructureRepositoryPort structure) {
    this.accounts = Objects.requireNonNull(accounts);
    this.structure = Objects.requireNonNull(structure);
  }

  @Override
  public AccessScope scopeOf(AuthPrincipal principal) {
    return currentUser(principal).scope();
  }

  @Override
  public AuthenticatedUser currentUser(AuthPrincipal principal) {
    UserAccount account =
        accounts
            .findById(principal.accountId())
            .orElseThrow(() -> new AuthenticationException("unknown account"));
    AccessScope scope =
        AccessPolicy.scopeOf(
            account, structure.findVerticals(), structure.findTeams(), structure.findPeople());
    return new AuthenticatedUser(account, scope);
  }
}
