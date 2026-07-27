package com.engperf.bootstrap.config;

import com.engperf.application.metrics.AiDashboardService;
import com.engperf.application.metrics.DoraDashboardService;
import com.engperf.application.metrics.FlowDashboardService;
import com.engperf.application.metrics.MetricCatalog;
import com.engperf.application.metrics.MetricsService;
import com.engperf.application.port.inbound.AiDashboardUseCase;
import com.engperf.application.port.inbound.DoraDashboardUseCase;
import com.engperf.application.port.inbound.FlowDashboardUseCase;
import com.engperf.application.port.inbound.MetricsQueryUseCase;
import com.engperf.application.port.outbound.EventStorePort;
import com.engperf.application.port.outbound.StructureRepositoryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composition root for the metrics engine. The reference "today" is a fixed {@link Clock} anchored
 * to the end of the seed window (env {@code METRICS_REFERENCE_DATE}) so trends and screenshots are
 * deterministic; the real ADO sync (S9) will move to a live clock.
 */
@Configuration
public class MetricsWiring {

  @Bean
  MetricCatalog metricCatalog() {
    return new MetricCatalog();
  }

  @Bean
  Clock metricsClock(@Value("${METRICS_REFERENCE_DATE:2026-06-30}") String referenceDate) {
    return Clock.fixed(Instant.parse(referenceDate + "T12:00:00Z"), ZoneOffset.UTC);
  }

  @Bean
  MetricsQueryUseCase metricsQueryUseCase(
      StructureRepositoryPort structure,
      EventStorePort events,
      MetricCatalog catalog,
      Clock metricsClock) {
    return new MetricsService(structure, events, catalog, metricsClock);
  }

  @Bean
  DoraDashboardUseCase doraDashboardUseCase(
      MetricsQueryUseCase metrics,
      MetricCatalog catalog,
      StructureRepositoryPort structure,
      Clock metricsClock) {
    return new DoraDashboardService(metrics, catalog, structure, metricsClock);
  }

  @Bean
  FlowDashboardUseCase flowDashboardUseCase(
      MetricsQueryUseCase metrics, MetricCatalog catalog, StructureRepositoryPort structure) {
    return new FlowDashboardService(metrics, catalog, structure);
  }

  @Bean
  AiDashboardUseCase aiDashboardUseCase(
      MetricsQueryUseCase metrics, MetricCatalog catalog, StructureRepositoryPort structure) {
    return new AiDashboardService(metrics, catalog, structure);
  }
}
