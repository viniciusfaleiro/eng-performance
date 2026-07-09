package com.engperf.application.structure;

import java.text.Normalizer;
import java.util.Locale;

/** Turns a display name into a URL-safe slug (matching the prototype's node ids). */
final class Slug {

  private Slug() {}

  static String of(String value) {
    String noAccents = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    return noAccents
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("(^-|-$)", "");
  }
}
