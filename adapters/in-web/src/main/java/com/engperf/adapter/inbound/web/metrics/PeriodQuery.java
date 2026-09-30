package com.engperf.adapter.inbound.web.metrics;

import com.engperf.application.port.inbound.PeriodRequest;
import com.engperf.domain.metrics.Frequency;
import java.util.Locale;

/**
 * The period-selecting parameters every metrics endpoint accepts, bound as one object.
 *
 * <p>Grouped rather than spread across each signature for two reasons. Six controllers used to
 * parse the frequency with six copies of the same switch — a parallel registry waiting to drift —
 * and every one of them now also needs the range and the rolling window. Spreading that would put
 * three more parameters on each method and leave the parsing duplicated six ways.
 *
 * <p>All fields are optional and, when all are absent, describe exactly what the system did before:
 * the calendar bucket of the default frequency containing today.
 *
 * @param freq the frequency label as the UI spells it ("Semanal", "Mensal", "Diário")
 * @param period the day identifying the calendar bucket, or the rolling window's anchor
 * @param from first day of a freely chosen range
 * @param to last day of a freely chosen range, inclusive as a person writes it
 * @param window length in days of a rolling window ending at the anchor
 */
public record PeriodQuery(String freq, String period, String from, String to, Integer window) {

  /** Spring binds query strings through this; absent parameters arrive as null. */
  public PeriodQuery() {
    this(null, null, null, null, null);
  }

  public Frequency frequency() {
    if (freq == null) {
      return Frequency.WEEKLY;
    }
    return switch (freq.strip().toLowerCase(Locale.ROOT)) {
      case "diário", "diario", "daily" -> Frequency.DAILY;
      case "mensal", "monthly" -> Frequency.MONTHLY;
      default -> Frequency.WEEKLY;
    };
  }

  public PeriodRequest toRequest() {
    return new PeriodRequest(frequency(), period, from, to, window);
  }

  /** The same selection with no period pointed at — the default period at this frequency. */
  public PeriodRequest currentRequest() {
    return new PeriodRequest(frequency(), null, null, null, null);
  }
}
