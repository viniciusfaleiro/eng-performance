package com.engperf.domain.structure;

/**
 * A committer identity discovered from the ingested data (commit email / PR user), optionally
 * linked to a {@link Person}. Unlinked identities live in the "Não atribuído" bucket and count
 * against attribution coverage.
 *
 * @param personId linked person id, or {@code null} when unmapped
 * @param commitCount events carried by this identity (seed value in this slice)
 */
public record CommitterIdentity(
    String identity, String displayName, String personId, long commitCount) {

  public CommitterIdentity {
    identity = Validation.text(identity, "identity");
    displayName = Validation.optional(displayName);
    personId = Validation.optional(personId);
    if (commitCount < 0) {
      throw new IllegalArgumentException("commitCount must not be negative");
    }
  }

  public boolean isMapped() {
    return personId != null;
  }

  public CommitterIdentity linkTo(String newPersonId) {
    return new CommitterIdentity(identity, displayName, newPersonId, commitCount);
  }

  public CommitterIdentity unlink() {
    return new CommitterIdentity(identity, displayName, null, commitCount);
  }
}
