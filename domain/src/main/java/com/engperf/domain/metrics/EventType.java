package com.engperf.domain.metrics;

/** The kind of raw activity event ingested from the source (seed today, Azure DevOps in S9). */
public enum EventType {
  COMMIT,
  PR,
  DEPLOY,
  WORKITEM
}
