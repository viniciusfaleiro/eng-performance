package com.engperf.application.metrics;

/** One work-type slice of a person's effort distribution: hours and share of total hours. */
public record WorkTypeSlice(String type, String label, double hours, double sharePct) {}
