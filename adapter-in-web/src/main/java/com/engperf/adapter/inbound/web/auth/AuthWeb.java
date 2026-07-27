package com.engperf.adapter.inbound.web.auth;

/** Shared constants for the auth web layer (request attributes populated by the token filter). */
public final class AuthWeb {

  /** Request attribute holding the verified {@link com.engperf.application.auth.AuthPrincipal}. */
  public static final String PRINCIPAL = "engperf.principal";

  /**
   * Request attribute holding the {@link com.engperf.application.auth.AuthenticatedUser} (account +
   * resolved {@link com.engperf.domain.access.AccessScope}).
   */
  public static final String USER = "engperf.user";

  private AuthWeb() {}
}
