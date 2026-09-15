package com.engperf.adapter.outbound.ado;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Restores commit messages that Azure DevOps truncated.
 *
 * <p>The commits list endpoint cuts a long message at roughly 400 characters and flags it with
 * {@code commentTruncated}. What gets cut is the body — and the body is exactly where the trailers
 * that mark AI authorship live. Without this, every commit with a long body is silently reported as
 * written without AI, which skews the whole IA dashboard downwards.
 */
final class CommitComments {

  private static final Logger LOG = LoggerFactory.getLogger(CommitComments.class);
  private static final String API = "api-version=7.1";

  private CommitComments() {}

  /**
   * The commit with its full message. Untruncated commits are returned as they came, so the extra
   * call costs nothing in the common case. A failed reload degrades the AI flag for that one commit
   * rather than failing the sync — a backfill of months should not die over a single message.
   */
  static JsonNode full(AdoRestClient client, String base, JsonNode commit, String token) {
    if (!commit.path("commentTruncated").asBoolean(false)) {
      return commit;
    }
    String id = commit.path("commitId").asText();
    try {
      return client.get(
          base + "/commits/" + URLEncoder.encode(id, StandardCharsets.UTF_8) + "?" + API, token);
    } catch (RuntimeException e) {
      LOG.warn("ADO sync: comentário truncado do commit {} não pôde ser recarregado", id, e);
      return commit;
    }
  }
}
