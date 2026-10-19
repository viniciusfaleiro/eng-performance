package com.engperf.domain.metrics;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Which side of the container/leaf line to read: everything, the items that group work, or the work
 * itself.
 *
 * <p>Defaults to {@link #ALL} — the reading that existed before the filter did — so no figure moves
 * until someone asks for a different one. The rule for what counts as a container lives in {@link
 * WorkItemRole}; this only says which side the reader wants.
 */
public enum ContainerFilter {
  ALL("all"),
  CONTAINERS("containers"),
  LEAVES("leaves");

  private final String key;

  ContainerFilter(String key) {
    this.key = key;
  }

  /** The stable key, as sent over the wire. */
  public String key() {
    return key;
  }

  public static ContainerFilter none() {
    return ALL;
  }

  /** The filter with this key, empty when the key is unknown or absent. Case-insensitive. */
  public static Optional<ContainerFilter> byKey(String key) {
    if (key == null || key.isBlank()) {
      return Optional.empty();
    }
    String k = key.strip().toLowerCase(Locale.ROOT);
    for (ContainerFilter f : values()) {
      if (f.key.equals(k)) {
        return Optional.of(f);
      }
    }
    return Optional.empty();
  }

  public boolean isEmpty() {
    return this == ALL;
  }

  /** Whether this event belongs to the selected side. {@link #ALL} takes everything. */
  public boolean matches(RawEvent event, Set<String> parentIds) {
    if (this == ALL) {
      return true;
    }
    boolean container = WorkItemRole.isContainer(event, parentIds);
    return this == CONTAINERS ? container : !container;
  }
}
