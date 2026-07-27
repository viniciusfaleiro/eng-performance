package com.engperf.application.port.outbound;

/** Outbound port: one-way hashing of account passwords (implemented with BCrypt in the adapter). */
public interface PasswordHasher {

  String hash(String rawPassword);

  /** Verifies a raw password against a stored hash without exposing either. */
  boolean matches(String rawPassword, String storedHash);
}
