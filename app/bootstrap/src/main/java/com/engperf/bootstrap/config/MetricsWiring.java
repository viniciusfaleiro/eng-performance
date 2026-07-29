package com.engperf.bootstrap.config;

import com.engperf.application.metrics.AiDashboardService;
import com.engperf.application.metrics.ComparisonHeatmapService;
import com.engperf.application.metrics.DoraDashboardService;
import com.engperf.application.metrics.FlowDashboardService;
import com.engperf.application.metrics.IndividualDashboardService;
import com.engperf.application.metrics.MetricCatalog;
import com.engperf.application.metrics.MetricsService;
import com.engperf.application.port.inbound.AiDashboardUseCase;
import com.engperf.application.port.inbound.ComparisonHeatmapUseCase;
import com.engperf.application.port.inbound.DoraDashboardUseCase;
import com.engperf.application.port.inbound.FlowDashboardUseCase;
import com.engperf.application.port.inbound.IndividualDashboardUseCase;
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
 * Composition root for the metrics engine. The reference "today" follows the real wall clock so
 * continuously-ingested ADO data stays visible; an optional {@code METRICS_REFERENCE_DATE} pins it
 * to a fixed day for deterministic demos/screenshots. (Tests inject their own {@link Clock}.)
 */
@Configuration
public class MetricsWiring {

  @Bean
  MetricCatalog metricCatalog() {
    return new MetricCatalog();
  }

  @Bean
  Clock metricsClock(@Value("${METRICS_REFERENCE_DATE:}") String referenceDate) {
    if (referenceDate == null || referenceDate.isBlank()) {
      return Clock.systemUTC(); // follow real time — live ADO events must not fall past a fixed day
    }
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

  @Bean
  ComparisonHeatmapUseCase comparisonHeatmapUseCase(
      MetricsQueryUseCase metrics,
      MetricCatalog catalog,
      StructureRepositoryPort structure,
      AiDashboardUseCase ai) {
    return new ComparisonHeatmapService(metrics, catalog, structure, ai);
  }

  @Bean
  IndividualDashboardUseCase individualDashboardUseCase(
      StructureRepositoryPort structure,
      EventStorePort events,
      MetricsQueryUseCase metrics,
      Clock metricsClock) {
    return new IndividualDashboardService(structure, events, metrics, metricsClock);
  }
}
