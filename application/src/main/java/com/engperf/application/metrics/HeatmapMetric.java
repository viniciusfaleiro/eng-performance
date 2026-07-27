package com.engperf.application.metrics;

/** One column of the comparison heatmap: a metric's key, label and unit. */
public record HeatmapMetric(String key, String label, String unit) {}
