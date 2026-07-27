package com.engperf.application.metrics;

import java.util.List;

/**
 * The composed IA dashboard for a node: the IA cards (AI share, adoption, impact), the AI-adoption
 * ranking of the node's children (verticals at the overview, teams within a vertical, nothing for a
 * team or person — people are never compared publicly), and the AI-vs-non-AI cycle-time series that
 * feeds the with/without comparison chart.
 */
public record AiDashboard(
    String nodeId,
    String childType,
    List<AiCard> cards,
    List<AdoptionRank> adoption,
    List<Double> cycleWithAi,
    List<Double> cycleWithoutAi) {

  public AiDashboard {
    cards = List.copyOf(cards);
    adoption = List.copyOf(adoption);
    cycleWithAi = List.copyOf(cycleWithAi);
    cycleWithoutAi = List.copyOf(cycleWithoutAi);
  }
}
