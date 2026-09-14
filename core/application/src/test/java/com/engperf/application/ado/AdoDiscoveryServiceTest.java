package com.engperf.application.ado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.engperf.application.port.inbound.AdoDiscoveryUseCase.Session;
import com.engperf.application.port.inbound.RepositoryUseCase;
import com.engperf.application.port.outbound.AdoAuthPort;
import com.engperf.application.port.outbound.AdoRepositoryDiscoveryPort;
import com.engperf.domain.structure.Repository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdoDiscoveryServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-06-30T12:00:00Z"), ZoneOffset.UTC);

  private final FakeAuth auth = new FakeAuth();
  private final FakeDiscoveryPort discoveryPort = new FakeDiscoveryPort();
  private final FakeRepositories repositories = new FakeRepositories();
  private final AdoDiscoveryService service =
      new AdoDiscoveryService(auth, discoveryPort, repositories, Runnable::run, CLOCK);

  @Test
  void computesInsertAndRemoveScopedToTheGivenOrganizationAndProject() {
    repositories.save(new Repository("repoA", "orgX", "ProjP", "t:1", null));
    repositories.save(new Repository("repoC", "orgX", "ProjP", null, null));
    repositories.save(new Repository("repoZ", "orgY", "ProjQ", null, null)); // different pair
    discoveryPort.result =
        List.of(new DiscoveredRepository("repoA", null), new DiscoveredRepository("repoB", "Prod"));

    Session s = service.start("orgX", "ProjP");
    RepositoryDiscoveryStatus status = service.status(s.sessionId()).orElseThrow();

    assertThat(status.done()).isTrue();
    assertThat(status.failed()).isFalse();
    assertThat(status.toInsert()).extracting(DiscoveredRepository::key).containsExactly("repoB");
    assertThat(status.toRemove()).containsExactly("repoC");
    // Unrelated (org, project) is never touched by the diff.
    assertThat(status.toRemove()).doesNotContain("repoZ");
  }

  @Test
  void applyRegistersInsertsAndDeletesRemovalsThenIsIdempotent() {
    repositories.save(new Repository("repoC", "orgX", "ProjP", null, null));
    discoveryPort.result = List.of(new DiscoveredRepository("repoB", "Prod"));

    Session s = service.start("orgX", "ProjP");
    service.apply(s.sessionId());

    assertThat(repositories.repositories())
        .extracting(Repository::key)
        .containsExactly("repoB"); // repoC removed, repoB inserted
    assertThat(repositories.repositories().get(0).teamId()).isNull(); // unmapped, like a manual add
    assertThat(repositories.repositories().get(0).productionStage()).isEqualTo("Prod");

    service.apply(s.sessionId()); // second apply — no duplicate insert, no re-delete
    assertThat(repositories.repositories()).extracting(Repository::key).containsExactly("repoB");
  }

  @Test
  void applyOnUnknownSessionIsRejected() {
    assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(() -> service.apply("nope"));
  }

  @Test
  void applyOnAFailedSessionIsRejected() {
    auth.declineLogin = true;
    Session s = service.start("orgX", "ProjP");

    assertThat(service.status(s.sessionId()).orElseThrow().failed()).isTrue();
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> service.apply(s.sessionId()));
  }

  private static final class FakeAuth implements AdoAuthPort {
    boolean declineLogin;

    @Override
    public DeviceCodePrompt beginDeviceCode() {
      return new DeviceCodePrompt("ABC-123", "https://microsoft.com/devicelogin", "dev", 0, 900);
    }

    @Override
    public Optional<String> poll(String deviceCode) {
      if (declineLogin) {
        throw new AdoAuthException("declined");
      }
      return Optional.of("token");
    }
  }

  private static final class FakeDiscoveryPort implements AdoRepositoryDiscoveryPort {
    List<DiscoveredRepository> result = List.of();

    @Override
    public List<DiscoveredRepository> discover(
        String accessToken, String organization, String project) {
      return result;
    }
  }

  private static final class FakeRepositories implements RepositoryUseCase {
    private final List<Repository> repos = new ArrayList<>();

    void save(Repository r) {
      repos.add(r);
    }

    @Override
    public List<Repository> repositories() {
      return new ArrayList<>(repos);
    }

    @Override
    public Repository register(
        String key, String organization, String project, String teamId, String productionStage) {
      Repository r = new Repository(key, organization, project, teamId, productionStage);
      repos.removeIf(x -> x.key().equals(key));
      repos.add(r);
      return r;
    }

    @Override
    public Repository mapToTeam(String repositoryKey, String teamId) {
      Repository current =
          repos.stream().filter(r -> r.key().equals(repositoryKey)).findFirst().orElseThrow();
      Repository updated = current.assignTo(teamId);
      repos.removeIf(x -> x.key().equals(repositoryKey));
      repos.add(updated);
      return updated;
    }

    @Override
    public void delete(String repositoryKey) {
      repos.removeIf(r -> r.key().equals(repositoryKey));
    }
  }
}
