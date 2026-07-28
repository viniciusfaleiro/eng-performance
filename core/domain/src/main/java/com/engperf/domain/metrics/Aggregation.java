package com.engperf.domain.metrics;

/**
 * How event values roll up to a node. {@code MEDIAN} and {@code RATIO} are recomputed over the
 * event population at the node (never composed from children); {@code SUM} adds; {@code SNAPSHOT}
 * takes each entity's last value in the bucket and sums them; {@code DISTINCT_RATIO} counts
 * distinct attributed people matching a per-event predicate over the distinct attributed people in
 * scope.
 */
public enum Aggregation {
  SUM,
  MEDIAN,
  RATIO,
  SNAPSHOT,
  DISTINCT_RATIO
}
