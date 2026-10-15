package com.engperf.domain.metrics;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The one declared table from an Azure DevOps work item type to a {@link WorkType}.
 *
 * <p>It lives here, and not in the adapter that reads Azure DevOps, because it is a product rule
 * and because everything that needs it — ingestion, the unmapped-type report, the Admin screen that
 * shows how types are read — has to give the same answer. A type absent from the table still
 * becomes {@link WorkType#FALLBACK}, so the distribution keeps its shape, but {@link
 * #isMapped(String)} is derived from this same table, so the fallback can be told apart from a
 * deliberate "documentation or other". Before that distinction existed, a type a team invented
 * yesterday was indistinguishable from documentation, and the work distribution shifted with
 * nothing on screen to say why.
 */
public final class WorkTypeMapping {

  private static final Map<String, WorkType> TABLE = build();

  private WorkTypeMapping() {}

  private static Map<String, WorkType> build() {
    Map<String, WorkType> m = new LinkedHashMap<>();
    m.put("bug", WorkType.BUG);
    m.put("defect", WorkType.BUG);
    m.put("issue", WorkType.BUG);
    m.put("user story", WorkType.FEATURE);
    m.put("feature", WorkType.FEATURE);
    m.put("product backlog item", WorkType.FEATURE);
    m.put("epic", WorkType.FEATURE);
    m.put("requirement", WorkType.FEATURE);
    m.put("tech debt", WorkType.TECH_DEBT);
    m.put("technical debt", WorkType.TECH_DEBT);
    m.put("débito técnico", WorkType.TECH_DEBT);
    m.put("maintenance", WorkType.MAINTENANCE);
    m.put("manutenção", WorkType.MAINTENANCE);
    m.put("chore", WorkType.MAINTENANCE);
    m.put("support", WorkType.MAINTENANCE);
    m.put("documentation or other", WorkType.DOCS);
    m.put("documentation", WorkType.DOCS);
    return Map.copyOf(m);
  }

  /**
   * The table itself, for the Admin screen — raw Azure DevOps type → category. A copy: the stored
   * map is already immutable, but handing it out directly is the kind of claim static analysis
   * cannot check, and the table is read once per screen.
   */
  public static Map<String, WorkType> table() {
    return Map.copyOf(TABLE);
  }

  /** The category of a raw type, {@link WorkType#FALLBACK} when the table does not know it. */
  public static WorkType of(String adoType) {
    return find(adoType).orElse(WorkType.FALLBACK);
  }

  /** The table's entry for a raw type, empty when there is none. Case-insensitive. */
  public static Optional<WorkType> find(String adoType) {
    if (adoType == null || adoType.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(TABLE.get(adoType.strip().toLowerCase(Locale.ROOT)));
  }

  /** Whether the table knows this raw type at all — {@code false} means it fell through. */
  public static boolean isMapped(String adoType) {
    return find(adoType).isPresent();
  }
}
