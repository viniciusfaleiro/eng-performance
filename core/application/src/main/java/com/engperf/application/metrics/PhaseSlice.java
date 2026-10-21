package com.engperf.application.metrics;

/**
 * One phase of the cycle-time breakdown: its key, its label, the hours it accounts for and the
 * share of the cycle it took.
 *
 * <p>The two numbers travel together because the question the breakdown answers is the share — "how
 * much of our time is waiting?" — and the hours are what makes that share checkable. The share is
 * also what produced the hours: it is the phase's part of the population's time, applied to the
 * Cycle Time card, which is why the three phases add up to it.
 */
public record PhaseSlice(String key, String label, double hours, double sharePct) {}
