package com.engperf.domain.config;

import com.engperf.domain.common.Text;

/**
 * Convention that marks a work item as impeded: the work item field that signals it and the prefix
 * of the tag that signals it along with its reason.
 *
 * <p>Neither can be compiled in. The field is custom per organisation — it reaches the API by its
 * <em>reference name</em> ({@code Impediment}, {@code Custom.Impediment}, …), not by the label the
 * board displays — and the tag prefix is the team's own wording, in the team's own language.
 *
 * <p>Both empty is a valid configuration and means "this board has no impediment signal": nothing
 * is recognised as impeded and every flow figure is what it would be without this convention. That
 * is the default, so a board that does not use the signal is never reinterpreted by a guess.
 */
public record ImpedimentConvention(String fieldName, String tagPrefix) {

  public ImpedimentConvention {
    fieldName = Text.optional(fieldName);
    tagPrefix = Text.optional(tagPrefix);
  }

  /**
   * The convention that recognises nothing — a new instance rather than a shared constant, because
   * a record compares by value and a cached one would only read as a singleton to static analysis.
   */
  public static ImpedimentConvention off() {
    return new ImpedimentConvention(null, null);
  }

  /** Whether either signal is configured — false means the overlay is switched off entirely. */
  public boolean enabled() {
    return fieldName != null || tagPrefix != null;
  }
}
