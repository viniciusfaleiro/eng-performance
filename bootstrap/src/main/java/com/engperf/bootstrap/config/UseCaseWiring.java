package com.engperf.bootstrap.config;

import com.engperf.application.EchoService;
import com.engperf.application.port.inbound.EchoUseCase;
import com.engperf.application.port.outbound.EchoCounterPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires framework-free application services to their ports.
 *
 * <p>The application layer has no Spring dependency (enforced by ArchUnit); the bootstrap is the
 * composition root that turns adapters into use-case beans.
 */
@Configuration
public class UseCaseWiring {

  @Bean
  EchoUseCase echoUseCase(EchoCounterPort echoCounterPort) {
    return new EchoService(echoCounterPort);
  }
}
