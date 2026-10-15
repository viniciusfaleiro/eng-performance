package com.engperf.application.metrics;

import java.util.List;

/**
 * What the Admin screen shows about work types: the declared Azure DevOps mapping, the five
 * categories themselves, and the raw types the ingestion met and did not recognise.
 *
 * <p>The unrecognised types are derived from the stored events, not from the last sync's memory: a
 * restart must not erase the answer, and the question — "which types is this organization using
 * that we read as documentation/other?" — is about the data we hold, not about one run.
 */
public record WorkTypeCatalog(
    List<Category> categories, List<Entry> mapping, List<UnmappedType> unmapped) {

  public WorkTypeCatalog {
    categories = List.copyOf(categories);
    mapping = List.copyOf(mapping);
    unmapped = List.copyOf(unmapped);
  }

  /** One of the five categories: its key and the label the UI shows. */
  public record Category(String key, String label) {}

  /** One line of the declared table: a raw Azure DevOps type read as a category. */
  public record Entry(String adoType, String category, String categoryLabel) {}

  /** A raw type nobody declared, with how many ingested work items carry it. */
  public record UnmappedType(String adoType, long count) {}
}
