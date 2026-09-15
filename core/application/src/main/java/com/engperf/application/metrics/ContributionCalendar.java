package com.engperf.application.metrics;

import com.engperf.domain.metrics.RawEvent;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The rolling map of commits per day behind the individual panel's calendar.
 *
 * <p>Every day in the window is present, including the empty ones — a calendar that only listed the
 * days someone committed would show a dense, flattering wall instead of the gaps, and the gaps are
 * half of what the picture is for.
 */
final class ContributionCalendar {

  /** 53 weeks × 7, so the grid always aligns to whole weeks ending on the reference day. */
  static final int DAYS = 371;

  private ContributionCalendar() {}

  /** The window of {@link #DAYS} days ending on {@code lastDay}, inclusive. */
  static List<CalendarDay> of(List<RawEvent> commits, LocalDate lastDay) {
    Map<LocalDate, Integer> byDay = new LinkedHashMap<>();
    for (LocalDate d = lastDay.minusDays(DAYS - 1L); !d.isAfter(lastDay); d = d.plusDays(1)) {
      byDay.put(d, 0);
    }
    for (RawEvent c : commits) {
      byDay.computeIfPresent(c.occurredOn(), (d, n) -> n + 1);
    }
    return byDay.entrySet().stream()
        .map(e -> new CalendarDay(e.getKey().toString(), e.getValue()))
        .toList();
  }
}
