package com.engperf.adapter.outbound.persistence;

import com.engperf.application.auth.AuthPrincipal;
import com.engperf.application.port.outbound.TokenService;
import com.engperf.domain.account.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Stateless session tokens backed by a signed JWT (HS256). No session store — the token itself
 * carries the principal, so authentication survives restarts by construction.
 */
@Component
public final class JwtTokenService implements TokenService {

  private final SecretKey key;
  private final Duration ttl;

  public JwtTokenService(
      @Value("${JWT_SECRET:dev-secret-change-me-please-min-32-bytes!}") String secret,
      @Value("${JWT_TTL:12h}") Duration ttl) {
    Objects.requireNonNull(secret, "secret must not be null");
    byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
    if (raw.length < 32) {
      throw new IllegalArgumentException("JWT secret must be at least 32 bytes for HS256");
    }
    this.key = Keys.hmacShaKeyFor(raw);
    this.ttl = Objects.requireNonNull(ttl, "ttl must not be null");
  }

  @Override
  public String issue(AuthPrincipal principal) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(principal.accountId())
        .claim("email", principal.email())
        .claim("role", principal.role().name())
        .claim("personId", principal.personId())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(ttl)))
        .signWith(key)
        .compact();
  }

  @Override
  public Optional<AuthPrincipal> verify(String token) {
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }
    try {
      Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
      Role role = Role.valueOf(claims.get("role", String.class));
      return Optional.of(
          new AuthPrincipal(
              claims.getSubject(),
              claims.get("email", String.class),
              role,
              claims.get("personId", String.class)));
    } catch (JwtException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
