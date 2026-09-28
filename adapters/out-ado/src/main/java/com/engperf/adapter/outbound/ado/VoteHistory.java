package com.engperf.adapter.outbound.ado;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Whether anyone ever asked for changes on a pull request.
 *
 * <p>Azure DevOps keeps only the reviewer's <em>current</em> vote on the PR object, so the ordinary
 * review cycle erases its own evidence: reject → author fixes → approve leaves {@code vote: 10},
 * and the PR reads as approved on the first pass. The history lives in the PR's system threads
 * instead ({@code CodeReviewThreadType = VoteUpdate}).
 *
 * <p>{@link #UNKNOWN} is not the same as "nobody objected": a PR whose history could not be read is
 * not evidence of a clean review, and counting it as one is how the metric inflates itself whenever
 * collection fails.
 */
record VoteHistory(boolean known, boolean changesRequested) {

  static final VoteHistory UNKNOWN = new VoteHistory(false, false);

  private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(VoteHistory.class);

  private static final String THREAD_TYPE = "CodeReviewThreadType";
  private static final String VOTE_RESULT = "CodeReviewVoteResult";
  private static final String VOTE_UPDATE = "VoteUpdate";

  /**
   * Fetches and reads one pull request's vote history. One extra call per PR — the ingestion loop
   * already pays one for the commits.
   *
   * <p>A failure yields {@link #UNKNOWN} instead of propagating: not knowing whether someone asked
   * for changes costs that PR's first-pass flag, while aborting would cost the whole repository.
   * What must not happen is a failure reading as approval, and it does not.
   */
  static VoteHistory fetch(AdoRestClient client, String base, long prId, String token) {
    try {
      return of(client.get(base + "/pullrequests/" + prId + "/threads?api-version=7.1", token));
    } catch (RuntimeException e) {
      LOG.warn(
          "ADO sync: histórico de votos do PR {} não pôde ser lido — {}", prId, e.getMessage());
      return UNKNOWN;
    }
  }

  /** Reads the vote-update threads of one pull request. */
  static VoteHistory of(JsonNode threads) {
    if (threads == null || !threads.has("value")) {
      return UNKNOWN;
    }
    boolean negative = false;
    for (JsonNode thread : threads.path("value")) {
      JsonNode props = thread.path("properties");
      if (!VOTE_UPDATE.equals(propertyValue(props, THREAD_TYPE))) {
        continue;
      }
      String vote = propertyValue(props, VOTE_RESULT);
      // -10 rejeitado, -5 aguardando autor: os dois devolvem o PR ao autor, que é o que "não passou
      // de primeira" quer dizer. Separá-los faria a métrica depender de qual botão o revisor
      // prefere.
      if (vote != null && vote.startsWith("-")) {
        negative = true;
      }
    }
    return new VoteHistory(true, negative);
  }

  /**
   * Whether this PR can be called a first-pass approval — never true when the history is unknown.
   */
  boolean firstPass() {
    return known && !changesRequested;
  }

  /**
   * ADO wraps each property as {@code {"$type":…,"$value":…}}, but plain scalars show up too
   * depending on the endpoint version — read both shapes rather than depend on one.
   */
  private static String propertyValue(JsonNode props, String name) {
    JsonNode p = props.path(name);
    if (p.isMissingNode() || p.isNull()) {
      return null;
    }
    JsonNode value = p.has("$value") ? p.path("$value") : p;
    return value.isNull() ? null : value.asText();
  }
}
