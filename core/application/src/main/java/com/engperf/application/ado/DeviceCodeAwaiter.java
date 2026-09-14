package com.engperf.application.ado;

import com.engperf.application.port.outbound.AdoAuthPort;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Polls an {@link AdoAuthPort} device-code login to completion — shared by every flow that needs an
 * interactive Entra token (the historical sync, repository discovery), so the poll/expiry/
 * interrupt handling lives in one place.
 */
public final class DeviceCodeAwaiter {

  private DeviceCodeAwaiter() {}

  /**
   * Blocks (on the caller's own thread — callers run this off the request thread) until the admin
   * completes login or the device code expires.
   *
   * @throws AdoAuthException on expiry, decline, or interruption
   */
  public static String await(AdoAuthPort auth, Clock clock, DeviceCodePrompt prompt) {
    Instant deadline = clock.instant().plusSeconds(prompt.expiresInSeconds());
    while (true) {
      Optional<String> token = auth.poll(prompt.deviceCode());
      if (token.isPresent()) {
        return token.get();
      }
      if (clock.instant().isAfter(deadline)) {
        throw new AdoAuthException("device code expired");
      }
      sleep(prompt.intervalSeconds());
    }
  }

  private static void sleep(int seconds) {
    try {
      Thread.sleep(Math.max(1, seconds) * 1000L);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AdoAuthException("interrupted while waiting for login");
    }
  }
}
