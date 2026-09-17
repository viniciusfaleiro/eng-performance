package com.engperf.adapter.outbound.ado;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Restores commit messages that Azure DevOps truncated.
 *
 * <p>The commits list endpoint cuts a long message at roughly 400 characters and flags it with
 * {@code commentTruncated}. What gets cut is the body — and the body is exactly where the trailers
 * that mark AI authorship live. Without this, every commit with a long body is silently reported as
 * written without AI, which skews the whole IA dashboard downwards.
 *
 * <p>There is no way to ask the list endpoint for the full message, so a reload costs one call per
 * truncated commit. Instances count how many they made: {@link #reloaded()} turns the cost of this
 * trade-off into something a real sync measures instead of something we guess at.
 */
final class CommitComments {

  private static final Logger LOG = LoggerFactory.getLogger(CommitComments.class);
  private static final String API = "api-version=7.1";

  private final AdoRestClient client;
  private final Predicate<String> isAi;
  private int reloaded;

  CommitComments(AdoRestClient client, Predicate<String> isAi) {
    this.client = client;
    this.isAi = isAi;
  }

  /**
   * The commit with whatever message is needed to judge it. Untruncated commits are returned as
   * they came, and so are truncated ones whose visible part already matches the AI convention —
   * reloading those could only confirm what is already known. A failed reload degrades the AI flag
   * for that one commit rather than failing the sync: a backfill of months should not die over a
   * single message.
   */
  JsonNode full(String base, JsonNode commit, String token) {
    String comment = commit.path("comment").asText("");
    if (!commit.path("commentTruncated").asBoolean(false) || isAi.test(comment)) {
      return commit;
    }
    String id = commit.path("commitId").asText();
    try {
      JsonNode full =
          client.get(
              base + "/commits/" + URLEncoder.encode(id, StandardCharsets.UTF_8) + "?" + API,
              token);
      reloaded++;
      return full;
    } catch (RuntimeException e) {
      LOG.warn("ADO sync: comentário truncado do commit {} não pôde ser recarregado", id, e);
      return commit;
    }
  }

  /**
   * Whether any of these commits was written with AI — the PR inherits the flag from its commits,
   * since a pull request has no marking of its own.
   *
   * <p>Goes through {@link #full} on purpose: the trailers that mark AI live at the end of the
   * message, which is exactly what Azure DevOps truncates. Testing the listed text directly would
   * reproduce, on pull requests, the bug already fixed for commits.
   */
  boolean anyAi(String base, JsonNode commits, String token) {
    for (JsonNode c : commits.path("value")) {
      if (isAi.test(full(base, c, token).path("comment").asText(""))) {
        return true;
      }
    }
    return false;
  }

  /** How many commits had to be fetched again because the list gave only part of the message. */
  int reloaded() {
    return reloaded;
  }
}
