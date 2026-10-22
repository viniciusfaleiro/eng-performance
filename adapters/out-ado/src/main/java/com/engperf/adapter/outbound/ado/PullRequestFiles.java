package com.engperf.adapter.outbound.ado;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.OptionalInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * How many files a pull request changes — the figure its Files tab shows.
 *
 * <p>It is the pull request's <strong>own diff</strong>: the target branch compared with the pull
 * request's latest iteration. It is deliberately not the sum of its commits' change counts, which
 * is what this replaced. That sum inflated the number by two independent routes — a merge commit
 * inside the pull request carries every file coming from the other branch, thousands of them and
 * none of them that pull request's work; and a file touched in five commits counted five times. A
 * pull request showing five files on screen was reported as 2309.
 *
 * <p>Two requests per pull request, plus one per extra page of a long diff. The iterations say
 * which version is current; the changes of that iteration, compared with the base, are the diff.
 * Instances count the requests they made ({@link #requests()}), so the cost of this reading is a
 * number a real sync reports instead of something we estimate.
 *
 * <p>Failure yields {@link OptionalInt#empty()} and never a partial count: a count missing a page
 * is indistinguishable from a genuine measurement, and the median that reads it cannot tell that it
 * is low. The pull request then falls out of PR Size and shows as not counted in the item list,
 * which is the honest reading — the measure does not exist.
 */
final class PullRequestFiles {

  private static final Logger LOG = LoggerFactory.getLogger(PullRequestFiles.class);
  private static final String API = "api-version=7.1";

  /** One page of the diff. Large enough that an ordinary pull request needs a single request. */
  private static final int PAGE = 2000;

  private final AdoRestClient client;
  private int requests;

  PullRequestFiles(AdoRestClient client) {
    this.client = client;
  }

  /** How many requests the diffs cost, for the sync summary. */
  int requests() {
    return requests;
  }

  /** The number of files in the pull request's diff, empty when it could not be read. */
  OptionalInt count(String base, long prId, String token) {
    try {
      OptionalInt iteration = latestIteration(base, prId, token);
      if (iteration.isEmpty()) {
        LOG.warn("ADO sync: PR {} sem iteração — fica sem contagem de arquivos", prId);
        return OptionalInt.empty();
      }
      return OptionalInt.of(files(base, prId, iteration.getAsInt(), token));
    } catch (RuntimeException e) {
      LOG.warn(
          "ADO sync: arquivos do PR {} não puderam ser lidos — {}; o PR fica sem contagem",
          prId,
          e.getMessage());
      return OptionalInt.empty();
    }
  }

  /**
   * The current version of the pull request. Earlier iterations are the history of pushes, and
   * counting them would repeat a file for the same reason summing commits did.
   */
  private OptionalInt latestIteration(String base, long prId, String token) {
    JsonNode res = get(base + "/pullrequests/" + prId + "/iterations?" + API, token);
    int latest = -1;
    for (JsonNode it : res.path("value")) {
      latest = Math.max(latest, it.path("id").asInt(-1));
    }
    return latest < 0 ? OptionalInt.empty() : OptionalInt.of(latest);
  }

  /**
   * The files of one iteration's diff against the pull request's base.
   *
   * <p>Paged while the response offers a {@code nextSkip}: stopping at the first page would cut a
   * long pull request in silence, and a number that is plausible and low is the kind nobody doubts.
   */
  private int files(String base, long prId, int iteration, String token) {
    int total = 0;
    int skip = 0;
    while (true) {
      JsonNode res =
          get(
              base
                  + "/pullrequests/"
                  + prId
                  + "/iterations/"
                  + iteration
                  + "/changes?$compareTo=0&$top="
                  + PAGE
                  + (skip > 0 ? "&$skip=" + skip : "")
                  + "&"
                  + API,
              token);
      for (JsonNode change : res.path("changeEntries")) {
        if (!isFolder(change.path("item"))) {
          total++;
        }
      }
      int next = res.path("nextSkip").asInt(0);
      if (next <= skip) {
        return total;
      }
      skip = next;
    }
  }

  private JsonNode get(String url, String token) {
    requests++;
    return client.get(url, token);
  }

  /**
   * Whether the entry describes a folder rather than a file. A rename is one entry and counts one,
   * however the change type is spelled ({@code "rename, edit"} is still a single file).
   */
  private static boolean isFolder(JsonNode item) {
    return item.path("isFolder").asBoolean(false)
        || "tree".equalsIgnoreCase(item.path("gitObjectType").asText(""));
  }
}
