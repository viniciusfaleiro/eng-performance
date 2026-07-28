package com.engperf.application.metrics;

import java.util.List;

/**
 * One structure in a ranking (a vertical or a team — never a person), with its four DORA cards.
 * Rows are ordered by the ranking's primary metric, best-first per that metric's direction.
 */
public record RankingRow(String nodeId, String label, List<DoraCard> cards) {

  public RankingRow {
    cards = List.copyOf(cards);
  }
}
