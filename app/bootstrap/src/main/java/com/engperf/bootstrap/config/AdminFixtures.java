package com.engperf.bootstrap.config;

import com.engperf.application.port.inbound.PlatformConfigUseCase;
import com.engperf.application.port.inbound.UserAccountUseCase;
import com.engperf.domain.account.AccountStatus;
import com.engperf.domain.account.Role;
import com.engperf.domain.config.AiStrategy;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Seeds the prototype's accounts and platform config into the DB (idempotent). Placeholder data —
 * login/RBAC (S2) and the real ADO sync (S9) replace parts of this later.
 */
@Component
@Order(2)
class AdminFixtures implements CommandLineRunner {

  private static final String DEFAULT_PASSWORD = "prototipo";
  private static final String AI_REGEX =
      "(?i)(co-authored-by:\\s*(copilot|claude)|assisted-by|\\[ai\\])";

  private final UserAccountUseCase users;
  private final PlatformConfigUseCase config;

  AdminFixtures(UserAccountUseCase users, PlatformConfigUseCase config) {
    this.users = users;
    this.config = config;
  }

  @Override
  public void run(String... args) {
    if (users.accounts().isEmpty()) {
      seedUsers();
    }
    if (config.aiConvention().trailer() == null && config.aiConvention().regex() == null) {
      seedConfig();
    }
  }

  private void seedUsers() {
    user("Admin Root", "admin@empresa.com", Role.ADMIN, AccountStatus.ACTIVE, null);
    user("Ana Souza", "ana.souza@empresa.com", Role.MANAGER, AccountStatus.ACTIVE, "p:ana-souza");
    user(
        "Bruno Lima",
        "bruno.lima@empresa.com",
        Role.CONTRIBUTOR,
        AccountStatus.ACTIVE,
        "p:bruno-lima");
    user(
        "Eduardo Alves",
        "eduardo.alves@empresa.com",
        Role.MANAGER,
        AccountStatus.ACTIVE,
        "p:eduardo-alves");
    user("Paula Executiva", "paula@empresa.com", Role.EXEC, AccountStatus.ACTIVE, null);
    user("Novo Convidado", "convidado@empresa.com", Role.CONTRIBUTOR, AccountStatus.INVITED, null);
    user("Ex-Colaborador", "ex@empresa.com", Role.CONTRIBUTOR, AccountStatus.DISABLED, null);
  }

  private void user(String name, String email, Role role, AccountStatus status, String personId) {
    users.create(name, email, DEFAULT_PASSWORD, role, status, personId);
  }

  private void seedConfig() {
    // No ADO org/PAT to seed — ingestion is per-repository (device-code). Only the AI convention.
    config.saveAiConvention(AiStrategy.TRAILER, "Co-authored-by: Copilot", null, AI_REGEX, false);
  }
}
