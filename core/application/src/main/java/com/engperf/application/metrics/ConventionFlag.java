package com.engperf.application.metrics;

import java.util.List;

/**
 * A coaching signal that this contributor may not be following an agreed engineering convention
 * (see {@code docs/convencoes-adocao-times.xlsx}), inferred from their own activity. {@code
 * severity} is {@code "warn"} (a likely broken convention worth checking with the dev) or {@code
 * "info"} (lower-confidence heads-up); {@code reference} points at the convention; {@code metrics}
 * are the metrics the broken convention distorts. {@code code} is the convention's stable number in
 * the agreed checklist, so the UI can line a flag up with its catalog entry. Coaching-only — never
 * aggregated or ranked.
 */
public record ConventionFlag(
    String code,
    String severity,
    String reference,
    String title,
    String detail,
    List<String> metrics) {

  public ConventionFlag {
    metrics = List.copyOf(metrics);
  }
}
