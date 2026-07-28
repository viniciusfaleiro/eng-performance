package com.engperf.domain.metrics;

/**
 * The three cut points that split a metric value into the four benchmark {@link Tier}s. They are
 * interpreted by the metric's {@link Direction}: for higher-is-better a value {@code >= elite} is
 * ELITE; for lower-is-better a value {@code <= elite} is ELITE.
 */
public record TierBands(double elite, double alto, double medio) {}
