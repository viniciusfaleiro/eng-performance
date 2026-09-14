package com.engperf.adapter.inbound.web.admin;

import com.engperf.adapter.inbound.web.admin.AdoDiscoveryDtos.DiscoverRequest;
import com.engperf.adapter.inbound.web.admin.AdoDiscoveryDtos.DiscoveryStartDto;
import com.engperf.adapter.inbound.web.admin.AdoDiscoveryDtos.DiscoveryStatusDto;
import com.engperf.application.port.inbound.AdoDiscoveryUseCase;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-triggered repository discovery for one Azure DevOps (organization, project): {@code POST
 * .../discover} starts the interactive device-code job and returns the login prompt; {@code GET
 * .../status} reports the diff once it is ready; {@code POST .../apply} applies it.
 */
@RestController
public class AdoDiscoveryController {

  private final AdoDiscoveryUseCase discovery;

  public AdoDiscoveryController(AdoDiscoveryUseCase discovery) {
    this.discovery = discovery;
  }

  @PostMapping("/api/admin/repositories/discover")
  public DiscoveryStartDto start(@RequestBody DiscoverRequest request) {
    return DiscoveryStartDto.from(discovery.start(request.organization(), request.project()));
  }

  @GetMapping("/api/admin/repositories/discover/status")
  public DiscoveryStatusDto status(@RequestParam String sessionId) {
    return discovery
        .status(sessionId)
        .map(DiscoveryStatusDto::from)
        .orElseThrow(() -> new NoSuchElementException("unknown discovery session: " + sessionId));
  }

  @PostMapping("/api/admin/repositories/discover/apply")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void apply(@RequestParam String sessionId) {
    discovery.apply(sessionId);
  }
}
