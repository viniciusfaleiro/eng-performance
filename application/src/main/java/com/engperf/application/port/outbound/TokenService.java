package com.engperf.application.port.outbound;

import com.engperf.application.auth.AuthPrincipal;
import java.util.Optional;

/** Outbound port: issues and verifies stateless session tokens (implemented with JWT). */
public interface TokenService {

  String issue(AuthPrincipal principal);

  Optional<AuthPrincipal> verify(String token);
}
