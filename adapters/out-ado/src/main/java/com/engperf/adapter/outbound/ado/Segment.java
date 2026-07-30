package com.engperf.adapter.outbound.ado;

/**
 * The flow segment a work-item state belongs to, reconstructed from its board columns. {@code
 * ACTIVE} and {@code REVIEW} are working states (they count as active for flow efficiency); {@code
 * WAITING} is idle/blocked/backlog; {@code DONE} is terminal (item completed). Derived from the
 * Azure DevOps state category when available, else a configurable name heuristic.
 */
enum Segment {
  WAITING,
  ACTIVE,
  REVIEW,
  DONE
}
