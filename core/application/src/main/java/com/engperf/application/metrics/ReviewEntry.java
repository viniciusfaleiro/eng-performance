package com.engperf.application.metrics;

/**
 * One review behind a reported figure. A review has no standalone record in Azure DevOps, so the
 * pull request it was made on <em>is</em> its record, and {@code url} points there.
 *
 * <p>The reviewer is deliberately absent from the entries of reviews <em>received</em>: the list
 * names the pull requests that were reviewed, never who reviewed them. Totalling "four of your PRs
 * were rejected by this person" would turn one person's coaching view into a comparison between
 * two, which is the use this product refuses.
 */
public record ReviewEntry(
    String id, String title, String url, String decision, String occurredOn, int comments) {}
