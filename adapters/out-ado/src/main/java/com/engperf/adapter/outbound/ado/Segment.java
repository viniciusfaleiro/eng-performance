package com.engperf.adapter.outbound.ado;

/**
 * The flow segment a work-item state belongs to, reconstructed from its board columns. {@code
 * ACTIVE} and {@code REVIEW} are working states (they count as active for flow efficiency); {@code
 * QUEUE} and {@code WAITING} are both idle; {@code DONE} is terminal (item completed). Derived from
 * the Azure DevOps state category when available, else a configurable name heuristic.
 *
 * <p>{@code QUEUE} and {@code WAITING} are the same thing for the cycle-time phases — both are
 * waiting, and the hours of any item are identical whichever of the two a state falls into. They
 * differ in one respect only: {@code QUEUE} is a queue the item reached <em>after</em> the team
 * took it on, where it waits for the next hand, while {@code WAITING} is where it waits to be taken
 * on at all. That line is where the commitment starts, and it is the only thing that lets "work the
 * team has taken on and not delivered" be told apart from the backlog.
 */
enum Segment {
  WAITING,
  QUEUE,
  ACTIVE,
  REVIEW,
  DONE
}
