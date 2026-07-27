package com.engperf.application.metrics;

import java.util.List;

/**
 * The composed Fluxo dashboard for a node: the Fluxo cards, the four-phase cycle-time breakdown,
 * and the throughput×cycle scatter of the node's children ({@code childType} is {@code vertical},
 * {@code team}, or {@code null} for a team/person — the scatter is then empty). Never contains
 * people.
 */
public record FlowDashboard(
    String nodeId,
    String childType,
    List<FlowCard> cards,
    List<PhaseSlice> phases,
    List<ScatterPoint> scatter) {

  public FlowDashboard {
    cards = List.copyOf(cards);
    phases = List.copyOf(phases);
    scatter = List.copyOf(scatter);
  }
}
