package com.engperf.application.metrics;

import com.engperf.application.metrics.IndividualDashboard.PlatformAccess;
import com.engperf.application.port.outbound.UserAccountRepositoryPort;

/**
 * Whether a person has an account on the platform, and when it last signed in.
 *
 * <p>Uma pessoa e uma conta são coisas diferentes: a plataforma mede pessoas, e nem toda pessoa tem
 * login. Sem conta não há o que afirmar — a tela omite, em vez de sugerir que alguém deixou de usar
 * algo que nunca teve.
 */
final class PlatformAccessLookup {

  private PlatformAccessLookup() {}

  static PlatformAccess of(UserAccountRepositoryPort accounts, String personNodeId) {
    return accounts.findAll().stream()
        .filter(a -> personNodeId.equals(a.personId()))
        .findFirst()
        .map(a -> new PlatformAccess(true, a.lastLoginAt()))
        .orElse(PlatformAccess.NO_ACCOUNT);
  }
}
