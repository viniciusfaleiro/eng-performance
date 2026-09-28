package com.engperf.adapter.outbound.ado;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The ids of the work items changed since a date, collected by adaptive bisection.
 *
 * <p>WIQL caps a result at 20000 rows and cannot be paged, so a wide window is simply refused. The
 * only way through is to keep halving it until each half fits — which is lossless, unlike taking
 * the first 20000 rows and moving on.
 */
final class ChangedWorkItemIds {

  private static final Logger LOG = LoggerFactory.getLogger(ChangedWorkItemIds.class);
  private static final String API = "api-version=7.1";

  private ChangedWorkItemIds() {}

  /** Every id changed in {@code [sinceDate, today]}, in first-seen order. */
  static List<String> collect(
      AdoRestClient client, String org, String proj, String sinceDate, String token) {
    LinkedHashSet<String> ids = new LinkedHashSet<>();
    LocalDate end = LocalDate.now(ZoneOffset.UTC).plusDays(1); // exclusive → includes today
    collectRange(client, org, proj, LocalDate.parse(sinceDate), end, token, ids);
    return new ArrayList<>(ids);
  }

  /**
   * Query {@code [from, to)}; if ADO refuses the window for exceeding its cap, bisect and retry.
   */
  private static void collectRange(
      AdoRestClient client,
      String org,
      String proj,
      LocalDate from,
      LocalDate to,
      String token,
      Set<String> ids) {
    if (!from.isBefore(to)) {
      return;
    }
    try {
      String wiql =
          "{\"query\":\"SELECT [System.Id] FROM WorkItems WHERE [System.ChangedDate] >= '"
              + from
              + "' AND [System.ChangedDate] < '"
              + to
              + "' ORDER BY [System.ChangedDate] ASC\"}";
      JsonNode res = client.post(org + "/" + proj + "/_apis/wit/wiql?" + API, token, wiql);
      for (JsonNode wi : res.path("workItems")) {
        ids.add(wi.path("id").asText());
      }
    } catch (RuntimeException e) {
      long days = ChronoUnit.DAYS.between(from, to);
      if (!exceedsWiqlLimit(e) || days <= 1) {
        throw e; // a different failure, or a single day we can no longer subdivide
      }
      LocalDate mid = from.plusDays(days / 2);
      LOG.info(
          "ADO sync: janela {}..{} excede o limite do WIQL; bisseccionando em {}", from, to, mid);
      collectRange(client, org, proj, from, mid, token, ids);
      collectRange(client, org, proj, mid, to, token, ids);
    }
  }

  /** The VS402337 "result exceeds 20000 items" refusal — the signal to narrow the window. */
  private static boolean exceedsWiqlLimit(RuntimeException e) {
    String m = e.getMessage();
    return m != null && (m.contains("VS402337") || m.contains("exceeds the size limit"));
  }
}
