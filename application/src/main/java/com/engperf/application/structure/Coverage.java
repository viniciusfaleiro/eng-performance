package com.engperf.application.structure;

/** Attribution coverage: mapped events over total (rest sits in the "Não atribuído" bucket). */
public record Coverage(double attributedPercent, long attributed, long total) {

  public static Coverage of(long attributed, long total) {
    double pct = total == 0 ? 0.0 : (attributed * 100.0) / total;
    return new Coverage(pct, attributed, total);
  }
}
