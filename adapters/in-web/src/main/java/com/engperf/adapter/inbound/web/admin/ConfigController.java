package com.engperf.adapter.inbound.web.admin;

import com.engperf.adapter.inbound.web.admin.ConfigDtos.AdoView;
import com.engperf.adapter.inbound.web.admin.ConfigDtos.AiRequest;
import com.engperf.adapter.inbound.web.admin.ConfigDtos.AiView;
import com.engperf.adapter.inbound.web.admin.ConfigDtos.EmailRequest;
import com.engperf.adapter.inbound.web.admin.ConfigDtos.EmailView;
import com.engperf.adapter.inbound.web.admin.ConfigDtos.TestEmailRequest;
import com.engperf.application.port.inbound.PlatformConfigUseCase;
import com.engperf.domain.config.AiStrategy;
import com.engperf.domain.config.SmtpSettings;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound REST adapter for the platform configuration. The Azure DevOps connection is no longer
 * configured here (no org URL, no PAT — ingestion is per-repository, auth is device-code); only the
 * integration status is read. The AI convention and the outgoing email server are configured here.
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

  @GetMapping("/api/admin/email")
  public EmailView getEmail() {
    return EmailView.from(config.smtpSettings());
  }

  @PutMapping("/api/admin/email")
  public EmailView saveEmail(@RequestBody EmailRequest request) {
    String password =
        request.password() == null || request.password().isBlank() ? null : request.password();
    SmtpSettings settings =
        new SmtpSettings(
            request.enabled(),
            request.host(),
            request.port(),
            request.transportOrDefault(),
            request.username(),
            password,
            request.fromAddress(),
            request.fromName(),
            request.appBaseUrl());
    return EmailView.from(config.saveSmtpSettings(settings));
  }

  @PostMapping("/api/admin/email/test")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void sendTestEmail(@RequestBody TestEmailRequest request) {
    config.sendTestEmail(request.to());
  }
}
