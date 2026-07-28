package com.engperf.adapter.inbound.web.auth;

import com.engperf.application.auth.AuthPrincipal;
import com.engperf.application.auth.AuthenticatedUser;
import com.engperf.application.port.inbound.AuthorizationUseCase;
import com.engperf.application.port.outbound.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Verifies the {@code Authorization: Bearer <jwt>} token on every {@code /api/**} request,
 * populates the request with the authenticated principal + scope, and enforces the coarse gate:
 *
 * <ul>
 *   <li>{@code /api/auth/login} is public;
 *   <li>every other {@code /api/**} route requires a valid token (401 otherwise);
 *   <li>{@code /api/admin/**} additionally requires the admin flag (403 otherwise).
 * </ul>
 *
 * Fine-grained node/person scope checks (403) are enforced by the controllers via the scope stored
 * here. Non-{@code /api} paths (the served prototype and its assets) are never gated.
 */
public class AuthTokenFilter extends OncePerRequestFilter {

  private static final String BEARER = "Bearer ";

  private final TokenService tokens;
  private final AuthorizationUseCase authorization;

  public AuthTokenFilter(TokenService tokens, AuthorizationUseCase authorization) {
    this.tokens = tokens;
    this.authorization = authorization;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !path(request).startsWith("/api/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String path = path(request);

    AuthenticatedUser user = resolve(request);
    if (user != null) {
      request.setAttribute(AuthWeb.PRINCIPAL, principal(user));
      request.setAttribute(AuthWeb.USER, user);
    }

    if (isPublic(path)) {
      chain.doFilter(request, response);
      return;
    }
    if (user == null) {
      Problem.write(response, HttpStatus.UNAUTHORIZED, "authentication required");
      return;
    }
    if (path.startsWith("/api/admin/") && !user.scope().canConfigure()) {
      Problem.write(response, HttpStatus.FORBIDDEN, "administrator access required");
      return;
    }
    chain.doFilter(request, response);
  }

  private AuthenticatedUser resolve(HttpServletRequest request) {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header == null || !header.startsWith(BEARER)) {
      return null;
    }
    Optional<AuthPrincipal> principal = tokens.verify(header.substring(BEARER.length()).strip());
    if (principal.isEmpty()) {
      return null;
    }
    try {
      return authorization.currentUser(principal.get());
    } catch (RuntimeException ex) {
      // Token valid but account no longer resolvable (deleted): treat as unauthenticated.
      return null;
    }
  }

  private static AuthPrincipal principal(AuthenticatedUser user) {
    return new AuthPrincipal(
        user.account().id(),
        user.account().email(),
        user.account().role(),
        user.account().personId());
  }

  private static boolean isPublic(String path) {
    return path.equals("/api/auth/login");
  }

  private static String path(HttpServletRequest request) {
    String uri = request.getRequestURI();
    String context = request.getContextPath();
    if (context != null && !context.isEmpty() && uri.startsWith(context)) {
      return uri.substring(context.length());
    }
    return uri;
  }
}
