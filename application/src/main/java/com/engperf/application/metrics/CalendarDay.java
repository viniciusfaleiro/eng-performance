package com.engperf.application.metrics;

/** One day of the contribution calendar: an ISO date and the person's commit count that day. */
public record CalendarDay(String date, int count) {}
