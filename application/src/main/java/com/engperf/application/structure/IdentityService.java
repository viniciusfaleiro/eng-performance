package com.engperf.application.structure;

import com.engperf.application.port.inbound.IdentityUseCase;
import com.engperf.application.port.outbound.StructureRepositoryPort;
import com.engperf.domain.structure.CommitterIdentity;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/** Application service linking committer identities to people and reporting coverage. */
public final class IdentityService implements IdentityUseCase {

  private final StructureRepositoryPort repository;

  public IdentityService(StructureRepositoryPort repository) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
  }

  @Override
  public List<CommitterIdentity> identities() {
    return repository.findIdentities();
  }

  @Override
  public CommitterIdentity assign(String identity, String personId) {
    CommitterIdentity current =
        repository
            .findIdentity(identity)
            .orElseThrow(() -> new NoSuchElementException("identity not found: " + identity));
    if (personId != null && !personId.isBlank()) {
      repository
          .findPerson(personId)
          .orElseThrow(() -> new IllegalArgumentException("person not found: " + personId));
      return repository.saveIdentity(current.linkTo(personId));
    }
    return repository.saveIdentity(current.unlink());
  }

  @Override
  public Coverage coverage() {
    List<CommitterIdentity> all = repository.findIdentities();
    long total = all.stream().mapToLong(CommitterIdentity::commitCount).sum();
    long attributed =
        all.stream()
            .filter(CommitterIdentity::isMapped)
            .mapToLong(CommitterIdentity::commitCount)
            .sum();
    return Coverage.of(attributed, total);
  }
}
