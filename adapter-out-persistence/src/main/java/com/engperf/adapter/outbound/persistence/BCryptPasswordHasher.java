package com.engperf.adapter.outbound.persistence;

import com.engperf.application.port.outbound.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Hashes account passwords with BCrypt (via spring-security-crypto — no full Security stack). */
@Component
public class BCryptPasswordHasher implements PasswordHasher {

  private final PasswordEncoder encoder = new BCryptPasswordEncoder();

  @Override
  public String hash(String rawPassword) {
    return encoder.encode(rawPassword);
  }
}
