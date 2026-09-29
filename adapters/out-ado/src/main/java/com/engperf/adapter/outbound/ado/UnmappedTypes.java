package com.engperf.adapter.outbound.ado;

import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Azure DevOps work item types that fell through to the default work type during one sync.
 *
 * <p>The mapping matches types by name, so a team renaming a type — or adding one, as this team
 * already did twice — silently sends its items to "documentation/other". Nothing failed, no test
 * broke, and the work distribution quietly shifted. Counting them turns that into a line in the
 * sync log, which is the difference between noticing and not.
 */
final class UnmappedTypes {

  private static final Logger LOG = LoggerFactory.getLogger(UnmappedTypes.class);

  private final Map<String, Integer> counts = new TreeMap<>();

  /** Records one item whose type matched no rule. Blank types are ignored — nothing to report. */
  void record(String adoType) {
    if (adoType != null && !adoType.isBlank()) {
      counts.merge(adoType, 1, Integer::sum);
    }
  }

  /** Logs the tally, if any. Silent when every type was recognised — the common, boring case. */
  void report(String org, String proj) {
    if (counts.isEmpty()) {
      return;
    }
    LOG.info(
        "ADO sync: {}/{} — tipo(s) de work item sem mapeamento, classificados como docs/outros: {}."
            + " Se algum deveria ter categoria própria, ajuste AdoMapper.workType.",
        org,
        proj,
        counts);
  }

  Map<String, Integer> counts() {
    return Map.copyOf(counts);
  }
}
