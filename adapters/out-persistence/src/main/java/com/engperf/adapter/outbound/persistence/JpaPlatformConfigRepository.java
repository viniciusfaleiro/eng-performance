package com.engperf.adapter.outbound.persistence;

import com.engperf.application.port.outbound.PlatformConfigPort;
import com.engperf.domain.config.AdoIntegration;
import com.engperf.domain.config.AiConvention;
import com.engperf.domain.config.AiStrategy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL-backed adapter for {@link PlatformConfigPort} (single config row, id "default"). */
@Component
@Transactional
public class JpaPlatformConfigRepository implements PlatformConfigPort {

  private static final String ID = "default";

  private final AdoIntegrationJpaRepository ado;
  private final AiConventionJpaRepository ai;

  public JpaPlatformConfigRepository(
      AdoIntegrationJpaRepository ado, AiConventionJpaRepository ai) {
    this.ado = ado;
    this.ai = ai;
  }

  @Override
  @Transactional(readOnly = true)
  public AdoIntegration getAdoIntegration() {
    return ado.findById(ID)
        .map(JpaPlatformConfigRepository::toAdo)
        .orElseGet(() -> new AdoIntegration(false, null));
  }

  @Override
  public AdoIntegration saveAdoIntegration(AdoIntegration integration) {
    ado.save(new AdoIntegrationEntity(ID, integration.connected(), integration.lastValidatedAt()));
    return integration;
  }

  @Override
  @Transactional(readOnly = true)
  public AiConvention getAiConvention() {
    return ai.findById(ID)
        .map(JpaPlatformConfigRepository::toAi)
        .orElseGet(() -> new AiConvention(AiStrategy.TRAILER, null, null, null, false));
  }

  @Override
  public AiConvention saveAiConvention(AiConvention convention) {
    ai.save(
        new AiConventionEntity(
            ID,
            convention.strategy(),
            convention.trailer(),
            convention.tag(),
            convention.regex(),
            convention.caseSensitive()));
    return convention;
  }

  private static AdoIntegration toAdo(AdoIntegrationEntity e) {
    return new AdoIntegration(e.isConnected(), e.getLastValidatedAt());
  }

  private static AiConvention toAi(AiConventionEntity e) {
    return new AiConvention(
        e.getStrategy(), e.getTrailer(), e.getTag(), e.getRegex(), e.isCaseSensitive());
  }
}
