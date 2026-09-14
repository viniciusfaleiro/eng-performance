package com.engperf.application.port.outbound;

/** Outbound port: generate opaque single-use tokens and hash them for storage. */
public interface SecureTokenGenerator {

  /** Generates a new random raw token (never persisted as-is). */
  String generate();

  /** Hashes {@code rawToken} into the form that is safe to persist and look up. */
  String hash(String rawToken);
}
