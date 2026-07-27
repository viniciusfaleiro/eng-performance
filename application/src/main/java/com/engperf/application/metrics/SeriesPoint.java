package com.engperf.application.metrics;

import com.engperf.domain.metrics.MetricValue;

/** One bucket of a metric series: the bucket's start date (ISO string) and its value/evolution. */
public record SeriesPoint(String bucketStart, MetricValue value) {}
