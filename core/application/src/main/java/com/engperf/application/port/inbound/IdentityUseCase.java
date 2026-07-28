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
}
