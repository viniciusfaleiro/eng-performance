package com.engperf.domain.account;

/** Persona of a platform account (drives RBAC scope, enforced in S2). */
public enum Role {
  EXEC,
  MANAGER,
  CONTRIBUTOR,
  ADMIN
}
