package com.engperf.adapter.outbound.persistence;

import com.engperf.application.port.outbound.SecureTokenGenerator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * Generates a 256-bit random token (Base64 URL-safe, ~43 chars) and hashes it with SHA-256 for
 * storage. Unlike {@link BCryptPasswordHasher}, this is intentionally fast and unsalted: the token
 * itself is 256 bits of {@link SecureRandom}, not a human-chosen secret, so it is not attackable by
 * offline brute force — a slow, salted hash would only prevent the direct hash lookup this port
 * needs.
 */
@Component
public class Sha256SecureTokenGenerator implements SecureTokenGenerator {

  private final SecureRandom random = new SecureRandom();

  @Override
  public String generate() {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  @Override
  public String hash(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashed);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
