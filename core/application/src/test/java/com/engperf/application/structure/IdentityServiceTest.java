package com.engperf.application.structure;

import static org.assertj.core.api.Assertions.assertThat;

import com.engperf.application.port.inbound.IdentityUseCase;
import com.engperf.application.port.outbound.EventStorePort;
import com.engperf.application.port.outbound.UserAccountRepositoryPort;
import com.engperf.domain.account.AccountStatus;
import com.engperf.domain.account.Role;
import com.engperf.domain.account.UserAccount;
import com.engperf.domain.metrics.EventType;
import com.engperf.domain.metrics.RawEvent;
import com.engperf.domain.structure.CommitterIdentity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Identities are discovered from the ingested events and auto-linked to people by matching the
 * commit e-mail to a login account's e-mail; manual links and unmatched identities are respected.
 */
class IdentityServiceTest {

  private final FakeStructureRepository repo = new FakeStructureRepository();
  private final FakeEvents events = new FakeEvents();
  private final FakeAccounts accounts = new FakeAccounts();
  private final IdentityService service = new IdentityService(repo, events, accounts);

  @Test
  void reloadDiscoversIdentitiesAndAutoLinksByAccountEmail() {
    accounts.add(account("ana@x.com", "p:ana"));
    events.add(commit("c1", "ana@x.com"));
    events.add(commit("c2", "ana@x.com"));
    events.add(commit("c3", "ghost@x.com")); // no account → stays unmapped

    IdentityUseCase.Reload result = service.reload();

    assertThat(result.discovered()).isEqualTo(2); // ana + ghost rows created
    assertThat(result.linked()).isEqualTo(1); // only ana matched an account e-mail
    assertThat(byId("ana@x.com").personId()).isEqualTo("p:ana");
    assertThat(byId("ana@x.com").commitCount()).isEqualTo(2);
    assertThat(byId("ghost@x.com").personId()).isNull();
    assertThat(byId("ghost@x.com").commitCount()).isEqualTo(1);
  }

  @Test
  void autoLinkMatchesCaseInsensitivelyAndKeepsManualLinks() {
    repo.saveIdentity(new CommitterIdentity("Marco@X.com", "Marco", null, 5));
    repo.saveIdentity(new CommitterIdentity("bea@x.com", "Bea", "p:manual", 3)); // pre-linked
    accounts.add(account("marco@x.com", "p:marco"));
    accounts.add(account("bea@x.com", "p:other")); // must NOT override the manual link

    int linked = service.autoLink();

    assertThat(linked).isEqualTo(1);
    assertThat(byId("Marco@X.com").personId()).isEqualTo("p:marco");
    assertThat(byId("bea@x.com").personId()).isEqualTo("p:manual"); // preserved
  }

  private CommitterIdentity byId(String id) {
    return repo.findIdentity(id).orElseThrow();
  }

  private static UserAccount account(String email, String personId) {
    return new UserAccount(
        "u:" + email, "User", email, Role.CONTRIBUTOR, AccountStatus.ACTIVE, personId, "h:x");
  }

  private static RawEvent commit(String id, String identity) {
    return new RawEvent(
        id,
        EventType.COMMIT,
        Instant.parse("2026-06-10T10:00:00Z"),
        null,
        identity,
        null,
        null,
        false,
        Map.of());
  }

  private static final class FakeEvents implements EventStorePort {
    private final List<RawEvent> all = new ArrayList<>();

    void add(RawEvent e) {
      all.add(e);
    }

    @Override
    public void saveAll(Collection<RawEvent> batch) {
      all.addAll(batch);
    }

    @Override
    public List<RawEvent> findByTypeBetween(EventType type, Instant from, Instant to) {
      return all.stream().filter(e -> e.type() == type).toList();
    }

    @Override
    public long count() {
      return all.size();
    }
  }

  private static final class FakeAccounts implements UserAccountRepositoryPort {
    private final List<UserAccount> all = new ArrayList<>();

    void add(UserAccount a) {
      all.add(a);
    }

    @Override
    public UserAccount save(UserAccount account) {
      all.add(account);
      return account;
    }

    @Override
    public List<UserAccount> findAll() {
      return all;
    }

    @Override
    public Optional<UserAccount> findById(String id) {
      return all.stream().filter(a -> a.id().equals(id)).findFirst();
    }

    @Override
    public Optional<UserAccount> findByEmail(String email) {
      return all.stream().filter(a -> a.email().equals(email)).findFirst();
    }

    @Override
    public void deleteById(String id) {
      all.removeIf(a -> a.id().equals(id));
    }
  }
}
