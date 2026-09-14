package com.engperf.bootstrap.config;

import com.engperf.application.auth.AuthService;
import com.engperf.application.auth.AuthorizationService;
import com.engperf.application.auth.PasswordResetService;
import com.engperf.application.port.inbound.AuthUseCase;
import com.engperf.application.port.inbound.AuthorizationUseCase;
import com.engperf.application.port.inbound.PasswordResetUseCase;
import com.engperf.application.port.outbound.EmailSenderPort;
import com.engperf.application.port.outbound.PasswordHasher;
import com.engperf.application.port.outbound.PasswordResetTokenPort;
import com.engperf.application.port.outbound.PlatformConfigPort;
import com.engperf.application.port.outbound.SecureTokenGenerator;
import com.engperf.application.port.outbound.StructureRepositoryPort;
import com.engperf.application.port.outbound.TokenService;
import com.engperf.application.port.outbound.UserAccountRepositoryPort;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composition root for authentication + authorization. The framework-free services are wired to
 * their outbound ports; the JWT {@link TokenService} adapter is discovered by component scan.
 */
@Configuration
public class AuthWiring {

  @Bean
  AuthUseCase authUseCase(
      UserAccountRepositoryPort accounts, PasswordHasher passwordHasher, TokenService tokens) {
    return new AuthService(accounts, passwordHasher, tokens);
  }

  @Bean
  AuthorizationUseCase authorizationUseCase(
      UserAccountRepositoryPort accounts, StructureRepositoryPort structure) {
    return new AuthorizationService(accounts, structure);
  }

  @Bean
  PasswordResetUseCase passwordResetUseCase(
      UserAccountRepositoryPort accounts,
      PasswordResetTokenPort tokens,
      PasswordHasher passwordHasher,
      SecureTokenGenerator tokenGenerator,
      EmailSenderPort emailSender,
      PlatformConfigPort config) {
    return new PasswordResetService(
        accounts, tokens, passwordHasher, tokenGenerator, emailSender, config, Clock.systemUTC());
  }
}
