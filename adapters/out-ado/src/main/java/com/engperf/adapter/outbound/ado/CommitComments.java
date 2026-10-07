package com.engperf.adapter.outbound.ado;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The commit as the sync needs it, when the list endpoints do not give enough.
 *
 * <p>The commits list endpoint cuts a long message at roughly 400 characters and flags it with
 * {@code commentTruncated}. What gets cut is the body — and the body is exactly where the trailers
 * that mark AI authorship live. Without this, every commit with a long body is silently reported as
 * written without AI, which skews the whole IA dashboard downwards.
 *
 * <p>The same single-commit request is also the only way to learn how many files a commit changed.
 * Neither commit list carries {@code changeCounts}, and the single-commit endpoint returns it only
 * when asked explicitly — the two examples in the API reference differ in exactly that parameter.
 * One request therefore serves both needs, memoised per commit id, so a commit that needs its
 * untruncated message <em>and</em> its file count is fetched once.
 *
 * <p>A fetch costs one call per commit. Instances count how many they made: {@link #reloaded()}
 * turns the cost of this trade-off into something a real sync measures instead of something we
 * guess at.
 */
final class CommitComments {

  private static final Logger LOG = LoggerFactory.getLogger(CommitComments.class);

  /**
   * {@code changeCount=1} and not zero, because without the parameter the counts are absent
   * altogether; and not ten, because the change list itself is never read — only the totals, which
   * come through correct however much the list is truncated.
   */
  private static final String API = "changeCount=1&api-version=7.1";

  private final AdoRestClient client;
  private final Predicate<String> isAi;
  private final Map<String, JsonNode> fetched = new HashMap<>();
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
    JsonNode full = fetch(base, commit.path("commitId").asText(), token);
    return full == null ? commit : full;
  }

  /**
   * How many files a commit changed, or empty when the figure could not be obtained.
   *
   * <p>Sums every entry of {@code changeCounts} rather than the three familiar ones: the change
   * types the API can report include renames and property changes, and a rename is as much a
   * changed file as an edit. Reading a fixed three would quietly undercount.
   *
   * <p>Counts <em>items</em>, which is what the source offers. A commit that creates directories
   * counts them too, so it reads slightly larger than the files it touches — said on the card.
   */
  OptionalInt commitFiles(String base, JsonNode commit, String token) {
    JsonNode full = fetch(base, commit.path("commitId").asText(), token);
    if (full == null) {
      return OptionalInt.empty();
    }
    JsonNode counts = full.path("changeCounts");
    if (!counts.isObject() || counts.isEmpty()) {
      return OptionalInt.empty();
    }
    int total = 0;
    for (JsonNode n : counts) {
      total += n.asInt(0);
    }
    return OptionalInt.of(total);
  }

  /**
   * The commit's full form, fetched at most once per sync. {@code null} means the request failed —
   * a backfill of months must not die over one commit, so each caller degrades its own answer.
   */
  private JsonNode fetch(String base, String id, String token) {
    if (fetched.containsKey(id)) {
      return fetched.get(id);
    }
    JsonNode full = null;
    try {
      full =
          client.get(
              base + "/commits/" + URLEncoder.encode(id, StandardCharsets.UTF_8) + "?" + API,
              token);
      reloaded++;
    } catch (RuntimeException e) {
      LOG.warn("ADO sync: commit {} não pôde ser lido por inteiro", id, e);
    }
    fetched.put(id, full);
    return full;
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

  /**
   * How many files a pull request changed, summed over its commits.
   *
   * <p>Empty when any single commit's count is missing, and deliberately not the sum of the rest: a
   * total missing one commit looks exactly like a legitimate measurement, and the median that reads
   * it has no way to tell that it is low. The same rule the work distribution and flow efficiency
   * already follow — no data stays no data.
   */
  OptionalInt changedFiles(String base, JsonNode commits, String token) {
    int total = 0;
    boolean any = false;
    for (JsonNode c : commits.path("value")) {
      OptionalInt files = commitFiles(base, c, token);
      if (files.isEmpty()) {
        return OptionalInt.empty();
      }
      total += files.getAsInt();
      any = true;
    }
    return any ? OptionalInt.of(total) : OptionalInt.empty();
  }

  /** How many commits had to be fetched individually, for their message or their change counts. */
  int reloaded() {
    return reloaded;
  }
}
