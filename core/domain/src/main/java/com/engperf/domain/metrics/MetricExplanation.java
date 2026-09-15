package com.engperf.domain.metrics;

import com.engperf.domain.common.Text;

/**
 * How a displayed number is produced, in the words of whoever reads the dashboard.
 *
 * <p>Deliberately five fields instead of one paragraph: a single free-form text lets each author
 * decide what to cover, and the result is uneven — one metric explains its source, another its
 * statistic, none explains both. Splitting the answer makes the omission visible, both to the
 * reviewer and to the completeness test.
 *
 * @param rule the calculation in one or two sentences, in business language
 * @param source which event it derives from and what is read out of it
 * @param included which events are counted
 * @param excluded which events are deliberately left out — the part people most often assume wrong
 * @param example a worked calculation with illustrative numbers, ending in a result
 */
public record MetricExplanation(
    String rule, String source, String included, String excluded, String example) {

  public MetricExplanation {
    rule = Text.required(rule, "explanation rule");
    source = Text.required(source, "explanation source");
    included = Text.required(included, "explanation included");
    excluded = Text.required(excluded, "explanation excluded");
    example = Text.required(example, "explanation example");
  }
}
