package com.engperf.application.metrics;

/** One phase of the cycle-time breakdown: its key, label, and median hours for the node. */
public record PhaseSlice(String key, String label, double hours) {}
