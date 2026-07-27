package com.engperf.application.port.inbound;

import com.engperf.application.auth.AuthPrincipal;
import com.engperf.application.auth.AuthenticatedUser;
import com.engperf.domain.access.AccessScope;

/** Inbound port: resolve the access scope of an authenticated principal from the structure. */
public interface AuthorizationUseCase {

  AccessScope scopeOf(AuthPrincipal principal);

  AuthenticatedUser currentUser(AuthPrincipal principal);
}
