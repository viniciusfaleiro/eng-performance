package com.engperf.application.structure;

import com.engperf.application.port.inbound.IdentityUseCase;
import com.engperf.application.port.outbound.EventStorePort;
import com.engperf.application.port.outbound.StructureRepositoryPort;
import com.engperf.application.port.outbound.UserAccountRepositoryPort;
import com.engperf.domain.account.UserAccount;
import com.engperf.domain.metrics.EventType;
import com.engperf.domain.metrics.RawEvent;
import com.engperf.domain.structure.CommitterIdentity;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * Application service for committer identities: linking to people (manual and automatic), reporting
 * coverage, and reconciling the identity list from the ingested events. Auto-linking matches a
 * commit/PR identity (an e-mail) to the login account that carries that e-mail and its person.
 */
public final class IdentityService implements IdentityUseCase {

  // Bounds meaning "everything in the store" when re-deriving identities from all ingested events.
  private static final Instant DAWN = Instant.EPOCH;
  private static final Instant HORIZON = Instant.parse("2999-01-01T00:00:00Z");

  private final StructureRepositoryPort repository;
  private final EventStorePort events;
  private final UserAccountRepositoryPort accounts;

  public IdentityService(
      StructureRepositoryPort repository,
      EventStorePort events,
      UserAccountRepositoryPort accounts) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.events = Objects.requireNonNull(events, "events must not be null");
    this.accounts = Objects.requireNonNull(accounts, "accounts must not be null");
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

  @Override
  public Reload reload() {
    int discovered = discoverFromEvents();
    int linked = autoLink();
    return new Reload(discovered, linked);
  }

  @Override
  public int autoLink() {
    Map<String, String> personByEmail = new HashMap<>();
    for (UserAccount a : accounts.findAll()) {
      if (a.personId() != null) {
        personByEmail.put(a.email(), a.personId()); // account e-mail is already lower-cased
      }
    }
    if (personByEmail.isEmpty()) {
      return 0;
    }
    int linked = 0;
    for (CommitterIdentity ci : repository.findIdentities()) {
      if (ci.personId() != null) {
        continue; // keep manual/existing links
      }
      String personId = personByEmail.get(ci.identity().toLowerCase(Locale.ROOT));
      if (personId != null) {
        repository.saveIdentity(ci.linkTo(personId));
        linked++;
      }
    }
    return linked;
  }

  /**
   * Re-derive the identity rows from every ingested event, preserving links/display names and
   * setting the commit count. Idempotent: unchanged rows are not rewritten.
   */
  private int discoverFromEvents() {
    Map<String, Long> commitCounts = new HashMap<>();
    Set<String> seen = new LinkedHashSet<>();
    for (EventType type : EventType.values()) {
      for (RawEvent e : events.findByTypeBetween(type, DAWN, HORIZON)) {
        if (e.committerIdentity() == null) {
          continue;
        }
        seen.add(e.committerIdentity());
        if (type == EventType.COMMIT) {
          commitCounts.merge(e.committerIdentity(), 1L, Long::sum);
        }
      }
    }
    Map<String, CommitterIdentity> existing = new HashMap<>();
    repository.findIdentities().forEach(ci -> existing.put(ci.identity(), ci));
    int changed = 0;
    for (String id : seen) {
      long count = commitCounts.getOrDefault(id, 0L);
      CommitterIdentity prev = existing.get(id);
      String personId = prev != null ? prev.personId() : null;
      String displayName = prev != null && prev.displayName() != null ? prev.displayName() : id;
      CommitterIdentity next = new CommitterIdentity(id, displayName, personId, count);
      if (next.equals(prev)) {
        continue;
      }
      repository.saveIdentity(next);
      changed++;
    }
    return changed;
  }
}
