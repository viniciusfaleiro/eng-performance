package com.engperf.bootstrap.config;

import com.engperf.application.port.inbound.IdentityUseCase;
import com.engperf.application.port.inbound.RepositoryUseCase;
import com.engperf.application.port.inbound.StructureUseCase;
import com.engperf.application.port.outbound.StructureRepositoryPort;
import com.engperf.application.structure.IdentityService;
import com.engperf.application.structure.RepositoryService;
import com.engperf.application.structure.StructureService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composition root wiring the framework-free structure services to their outbound port. */
@Configuration
public class StructureWiring {

  @Bean
  StructureUseCase structureUseCase(StructureRepositoryPort repository) {
    return new StructureService(repository);
  }

  @Bean
  RepositoryUseCase repositoryUseCase(StructureRepositoryPort repository) {
    return new RepositoryService(repository);
  }

  @Bean
  IdentityUseCase identityUseCase(StructureRepositoryPort repository) {
    return new IdentityService(repository);
  }
}
