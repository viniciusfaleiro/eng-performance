package com.engperf.application.port.inbound;

import com.engperf.application.structure.Coverage;
import com.engperf.domain.structure.CommitterIdentity;
import java.util.List;

/** Inbound port: list committer identities, link/unlink them to people, and report coverage. */
public interface IdentityUseCase {

  List<CommitterIdentity> identities();

  /** Links the identity to a person; a {@code null} personId unlinks it (back to unattributed). */
  CommitterIdentity assign(String identity, String personId);

  Coverage coverage();

  /**
   * Discover committer identities from the ingested events (creating rows to map) and auto-link the
   * unmapped ones to people by e-mail. Safe to call anytime; idempotent.
   */
  Reload reload();

  /**
   * Link unmapped identities to people by matching a login account's e-mail. Returns links made.
   */
  int autoLink();

  /**
   * Outcome of {@link #reload()}: how many identity rows were discovered/updated and auto-linked.
   */
  record Reload(int discovered, int linked) {}
}
