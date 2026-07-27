package com.engperf.application.metrics;

import java.util.List;

/**
 * The composed DORA dashboard for a node: the four DORA cards for the node itself, and a ranking of
 * the node's children ({@code childType} is {@code vertical}, {@code team}, or {@code null} when
 * the node has no rankable children — a team or person). Rankings never contain people.
 */
public record DoraDashboard(
    String nodeId, String childType, List<DoraCard> cards, List<RankingRow> ranking) {

  public DoraDashboard {
    cards = List.copyOf(cards);
    ranking = List.copyOf(ranking);
  }
}
