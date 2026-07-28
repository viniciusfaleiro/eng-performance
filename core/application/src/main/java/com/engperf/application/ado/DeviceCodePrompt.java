package com.engperf.application.ado;

/**
 * The Entra device-code prompt shown to the admin: the code to type and where to type it, plus the
 * opaque {@code deviceCode} the backend polls with and the timing hints from Entra.
 */
public record DeviceCodePrompt(
    String userCode,
    String verificationUri,
    String deviceCode,
    int intervalSeconds,
    int expiresInSeconds) {}
