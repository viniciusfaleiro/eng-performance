package com.engperf.application.ado;

/** A hard failure in the device-code flow (declined, expired, or tenant policy). */
public class AdoAuthException extends RuntimeException {
  public AdoAuthException(String message) {
    super(message);
  }
}
