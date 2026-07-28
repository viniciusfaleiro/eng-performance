package com.engperf.bootstrap.config;

import com.engperf.application.account.UserAccountService;
import com.engperf.application.config.PlatformConfigService;
import com.engperf.application.port.inbound.PlatformConfigUseCase;
import com.engperf.application.port.inbound.UserAccountUseCase;
import com.engperf.application.port.outbound.PasswordHasher;
import com.engperf.application.port.outbound.PlatformConfigPort;
import com.engperf.application.port.outbound.UserAccountRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the framework-free account/config services to their outbound ports. */
@Configuration
public class AdminWiring {

  @Bean
  UserAccountUseCase userAccountUseCase(
      UserAccountRepositoryPort repository, PasswordHasher passwordHasher) {
    return new UserAccountService(repository, passwordHasher);
  }

  @Bean
  PlatformConfigUseCase platformConfigUseCase(PlatformConfigPort port) {
    return new PlatformConfigService(port);
  }
}
