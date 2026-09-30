package com.engperf.application.metrics;

/**
 * One work item counted behind a type's hours, with <strong>both</strong> figures it takes to check
 * the number: {@code elapsedHours} is how long the item was in progress inside the period, and
 * {@code countedHours} is what it actually contributed after each elapsed hour was divided among
 * the items in progress at the same time.
 *
 * <p>Neither figure explains the other on its own. The elapsed time does not explain the total —
 * the elapsed hours of concurrent items sum to more than the period has. The counted time does not
 * explain why an item open for three days shows twenty minutes. Their difference <em>is</em> the
 * parallelism, which is the fact that was missing when a distribution had to be audited by querying
 * the database.
 */
public record WorkItemEntry(
    String id, String title, String url, double elapsedHours, double countedHours) {}
