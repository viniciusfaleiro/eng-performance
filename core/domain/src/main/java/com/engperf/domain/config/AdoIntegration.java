package com.engperf.domain.config;

import com.engperf.domain.common.Text;
import java.time.Instant;

/**
 * Azure DevOps connection configuration. The PAT is stored only as a secret and never returned in
 * clear. Actually validating the connection and syncing is S9; here it is just persisted config.
 *
 * @param patSecret personal access token (secret), or {@code null}
 * @param lastValidatedAt when the connection was last confirmed, or {@code null}
 */
public record AdoIntegration(
    String organizationUrl,
    String patSecret,
    String productionStageRule,
    boolean connected,
    Instant lastValidatedAt) {

  public AdoIntegration {
    organizationUrl = Text.optional(organizationUrl);
    patSecret = Text.optional(patSecret);
    productionStageRule = Text.optional(productionStageRule);
  }

  public boolean hasSecret() {
    return patSecret != null;
  }
}
