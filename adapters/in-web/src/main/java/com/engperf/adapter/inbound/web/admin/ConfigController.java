package com.engperf.adapter.inbound.web.admin;

import com.engperf.adapter.inbound.web.admin.ConfigDtos.AdoView;
import com.engperf.adapter.inbound.web.admin.ConfigDtos.AiRequest;
import com.engperf.adapter.inbound.web.admin.ConfigDtos.AiView;
import com.engperf.application.port.inbound.PlatformConfigUseCase;
import com.engperf.domain.config.AiStrategy;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound REST adapter for the platform configuration. The Azure DevOps connection is no longer
 * configured here (no org URL, no PAT — ingestion is per-repository, auth is device-code); only the
 * integration status is read. The AI convention is still configured here.
 */
@RestController
public class ConfigController {

  private final PlatformConfigUseCase config;

  public ConfigController(PlatformConfigUseCase config) {
    this.config = config;
  }

  @GetMapping("/api/admin/integrations/azure-devops")
  public AdoView getAdo() {
    return AdoView.from(config.adoIntegration());
  }

  @GetMapping("/api/admin/ai-convention")
  public AiView getAiConvention() {
    return AiView.from(config.aiConvention());
  }

  @PutMapping("/api/admin/ai-convention")
  public AiView saveAiConvention(@RequestBody AiRequest request) {
    AiStrategy strategy =
        request.strategy() == null
            ? AiStrategy.TRAILER
            : AiStrategy.valueOf(request.strategy().strip().toUpperCase(Locale.ROOT));
    return AiView.from(
        config.saveAiConvention(
            strategy, request.trailer(), request.tag(), request.regex(), request.caseSensitive()));
  }
}
