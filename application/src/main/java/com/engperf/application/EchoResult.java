package com.engperf.application;

/**
 * Result of an echo.
 *
 * @param text the echoed text
 * @param sequence the 1-based count of echoes served so far
 */
public record EchoResult(String text, long sequence) {}
