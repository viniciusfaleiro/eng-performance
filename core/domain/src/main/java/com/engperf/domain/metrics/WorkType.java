package com.engperf.domain.metrics;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The kind of work an event is about — the reading dimension behind the "Tipo de trabalho" filter
 * and behind the individual panel's distribution.
 *
 * <p>Five categories, in the order the prototype's legend shows them. The list is closed on
 * purpose: it is the vocabulary the whole product speaks, and a sixth category would have to earn
 * its place in every chart, filter and explanation at once. A team's own Azure DevOps type reaches
 * one of these through {@link WorkTypeMapping}.
 */
public enum WorkType {
  FEATURE("feature", "Feature"),
  BUG("bug", "Bug"),
  TECH_DEBT("tech_debt", "Tech Debt"),
  MAINTENANCE("maintenance", "Manutenção"),
  DOCS("docs", "Docs/Outros");

  /** What an item with no recognised type becomes — see {@link WorkTypeMapping}. */
  public static final WorkType FALLBACK = DOCS;

  private final String key;
  private final String label;

  WorkType(String key, String label) {
    this.key = key;
    this.label = label;
  }

  /** The stable key, as stored in an event's detail and as sent over the wire. */
  public String key() {
    return key;
  }

  /** The display label, in the prototype's wording. */
  public String label() {
    return label;
  }

  /** All five, in legend order — the single list every chart and filter reads. */
  public static List<WorkType> all() {
    return List.of(values());
  }

  /** The type with this key, empty if the key is unknown or absent. Case-insensitive. */
  public static Optional<WorkType> byKey(String key) {
    if (key == null || key.isBlank()) {
      return Optional.empty();
    }
    String k = key.strip().toLowerCase(Locale.ROOT);
    return Arrays.stream(values()).filter(t -> t.key.equals(k)).findFirst();
  }
}
