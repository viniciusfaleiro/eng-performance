package com.engperf.domain.metrics;

/**
 * How a metric attributes an event: {@code PERSON} routes via committer identity → Person (and
 * as-of-event Team/Vertical); {@code REPO} routes via repository → Team.
 */
public enum AttributionScope {
  PERSON,
  REPO
}
