package com.engperf.adapter.inbound.web.admin;

import com.engperf.domain.config.AdoIntegration;
import com.engperf.domain.config.AiConvention;
import java.time.Instant;
import java.util.Locale;

/**
 * Request/response DTOs for the platform-config admin endpoints (ADO connection, AI convention).
 */
final class ConfigDtos {

  private ConfigDtos() {}

  record AdoView(
      String organizationUrl,
      boolean connected,
      String patRedacted,
      String productionStageRule,
      Instant lastValidatedAt) {

    static AdoView from(AdoIntegration a) {
      return new AdoView(
          a.organizationUrl(),
          a.connected(),
          a.hasSecret() ? "••••••••••••" : null,
          a.productionStageRule(),
          a.lastValidatedAt());
    }
  }

  record AdoRequest(
      String organizationUrl, String personalAccessToken, String productionStageRule) {}

  record AiView(String strategy, String trailer, String tag, String regex, boolean caseSensitive) {

    static AiView from(AiConvention c) {
      return new AiView(
          c.strategy().name().toLowerCase(Locale.ROOT),
          c.trailer(),
          c.tag(),
          c.regex(),
          c.caseSensitive());
    }
  }

  record AiRequest(
      String strategy, String trailer, String tag, String regex, boolean caseSensitive) {}
}
