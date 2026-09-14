package com.engperf.adapter.outbound.persistence;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Encrypts secrets at rest (currently only the SMTP password) with AES-256-GCM. The key comes from
 * the {@code CONFIG_ENCRYPTION_KEY} env var (32 bytes, Base64) — never from the database. Without a
 * key configured, {@link #encrypt} refuses rather than silently storing the secret in clear text,
 * and {@link #decrypt} treats undecryptable ciphertext as "no secret configured" (logged) rather
 * than failing the whole read.
 */
@Component
final class SecretCipher {

  private static final Logger LOG = LoggerFactory.getLogger(SecretCipher.class);
  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;

  private final byte[] key;
  private final SecureRandom random = new SecureRandom();

  SecretCipher(@Value("${CONFIG_ENCRYPTION_KEY:}") String base64Key) {
    if (base64Key == null || base64Key.isBlank()) {
      this.key = null;
      return;
    }
    byte[] decoded = Base64.getDecoder().decode(base64Key);
    if (decoded.length != 32) {
      throw new IllegalArgumentException("CONFIG_ENCRYPTION_KEY must decode to 32 bytes (AES-256)");
    }
    this.key = decoded;
  }

  boolean isConfigured() {
    return key != null;
  }

  /**
   * @throws IllegalStateException if no {@code CONFIG_ENCRYPTION_KEY} is configured
   */
  String encrypt(String plaintext) {
    if (key == null) {
      throw new IllegalStateException(
          "CONFIG_ENCRYPTION_KEY is not set — define it before saving an SMTP password");
    }
    try {
      byte[] iv = new byte[IV_BYTES];
      random.nextBytes(iv);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(
          Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
      byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      byte[] combined = new byte[iv.length + ciphertext.length];
      System.arraycopy(iv, 0, combined, 0, iv.length);
      System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
      return Base64.getEncoder().encodeToString(combined);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("failed to encrypt secret", e);
    }
  }

  /** Returns {@code null} (logged) if there is no key, or the ciphertext cannot be decrypted. */
  String decrypt(String ciphertextBase64) {
    if (key == null || ciphertextBase64 == null) {
      return null;
    }
    try {
      byte[] combined = Base64.getDecoder().decode(ciphertextBase64);
      byte[] iv = new byte[IV_BYTES];
      System.arraycopy(combined, 0, iv, 0, IV_BYTES);
      byte[] ciphertext = new byte[combined.length - IV_BYTES];
      System.arraycopy(combined, IV_BYTES, ciphertext, 0, ciphertext.length);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(
          Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
      return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      LOG.warn("could not decrypt stored SMTP password — treating as not configured", e);
      return null;
    }
  }
}
