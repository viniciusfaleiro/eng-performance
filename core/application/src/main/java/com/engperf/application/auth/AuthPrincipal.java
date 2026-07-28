package com.engperf.application.auth;

import com.engperf.domain.account.Role;

/** The authenticated identity carried by a session token (no secret material). */
public record AuthPrincipal(String accountId, String email, Role role, String personId) {}
