package com.engperf.domain.metrics;

/**
 * How event values roll up to a node. {@code MEDIAN} and {@code RATIO} are recomputed over the
 * event population at the node (never composed from children); {@code SUM} adds; {@code SNAPSHOT}
 * takes each entity's last value in the bucket and sums them.
 */
public enum Aggregation {
  SUM,
  MEDIAN,
  RATIO,
  SNAPSHOT
}
