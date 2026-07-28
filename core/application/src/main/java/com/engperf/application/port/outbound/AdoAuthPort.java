package com.engperf.application.port.outbound;

import com.engperf.application.ado.DeviceCodePrompt;
import java.util.Optional;

/**
 * Interactive Microsoft Entra device-code authentication for Azure DevOps — no PAT, no stored
 * secret. Implemented by the outbound ADO adapter.
 */
public interface AdoAuthPort {

  /** Begins a device-code flow; the returned prompt is shown to the admin to complete with MFA. */
  DeviceCodePrompt beginDeviceCode();

  /**
   * Polls once for the user's access token. Empty means the user has not finished logging in yet; a
   * value is the bearer token. Throws {@link com.engperf.application.ado.AdoAuthException} on a
   * terminal failure (declined/expired/blocked).
   */
  Optional<String> poll(String deviceCode);
}
