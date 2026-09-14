package com.engperf.application.ado;

import com.engperf.application.port.inbound.AdoDiscoveryUseCase;
import com.engperf.application.port.inbound.RepositoryUseCase;
import com.engperf.application.port.outbound.AdoAuthPort;
import com.engperf.application.port.outbound.AdoRepositoryDiscoveryPort;
import com.engperf.domain.structure.Repository;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrates repository discovery as a background job: begin device-code auth → poll for the
 * user's token → list the project's real repositories → diff against the registry. The diff is only
 * ever applied on an explicit {@link #apply} call — computing it never mutates the registry. Job
 * state lives in memory and is read back via {@link #status}, same as {@code AdoSyncService}.
 */
public final class AdoDiscoveryService implements AdoDiscoveryUseCase {

  private static final Logger LOG = LoggerFactory.getLogger(AdoDiscoveryService.class);

  private final AdoAuthPort auth;
  private final AdoRepositoryDiscoveryPort discovery;
  private final RepositoryUseCase repositories;
  private final Executor executor;
  private final Clock clock;

  private final Map<String, Job> jobs = new ConcurrentHashMap<>();

  public AdoDiscoveryService(
      AdoAuthPort auth,
      AdoRepositoryDiscoveryPort discovery,
      RepositoryUseCase repositories,
      Executor executor,
      Clock clock) {
    this.auth = Objects.requireNonNull(auth, "auth must not be null");
    this.discovery = Objects.requireNonNull(discovery, "discovery must not be null");
    this.repositories = Objects.requireNonNull(repositories, "repositories must not be null");
    this.executor = Objects.requireNonNull(executor, "executor must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  @Override
  public Session start(String organization, String project) {
    DeviceCodePrompt prompt = auth.beginDeviceCode();
    Job job = new Job(organization, project);
    String sessionId = UUID.randomUUID().toString();
    jobs.put(sessionId, job);
    executor.execute(() -> run(job, prompt));
    return new Session(sessionId, prompt);
  }

  @Override
  public Optional<RepositoryDiscoveryStatus> status(String sessionId) {
    return Optional.ofNullable(jobs.get(sessionId)).map(j -> j.snapshot(sessionId));
  }

  @Override
  public void apply(String sessionId) {
    Job job = jobs.get(sessionId);
    if (job == null) {
      throw new NoSuchElementException("unknown discovery session: " + sessionId);
    }
    if (!job.done || job.failed) {
      throw new IllegalStateException("discovery session not finished: " + sessionId);
    }
    if (job.applied) {
      return; // already applied — idempotent, never double-inserts or double-removes
    }
    for (DiscoveredRepository r : job.toInsert) {
      repositories.register(
          r.key(), job.organization, job.project, null, r.suggestedProductionStage());
    }
    for (String key : job.toRemove) {
      repositories.delete(key);
    }
    job.applied = true;
    LOG.info(
        "ADO discovery: diff aplicado em {}/{} — {} inserido(s), {} removido(s)",
        job.organization,
        job.project,
        job.toInsert.size(),
        job.toRemove.size());
  }

  private void run(Job job, DeviceCodePrompt prompt) {
    try {
      String token = DeviceCodeAwaiter.await(auth, clock, prompt);
      job.phase = "listing";
      List<DiscoveredRepository> found = discovery.discover(token, job.organization, job.project);
      Set<String> foundKeys = found.stream().map(r -> lower(r.key())).collect(Collectors.toSet());

      List<Repository> registered = repositoriesFor(job.organization, job.project);
      Set<String> registeredKeys =
          registered.stream().map(r -> lower(r.key())).collect(Collectors.toSet());

      List<DiscoveredRepository> toInsert =
          found.stream().filter(r -> !registeredKeys.contains(lower(r.key()))).toList();
      List<String> toRemove =
          registered.stream()
              .map(Repository::key)
              .filter(k -> !foundKeys.contains(lower(k)))
              .toList();

      job.finish(toInsert, toRemove);
      LOG.info(
          "ADO discovery: {}/{} — {} a inserir, {} a remover",
          job.organization,
          job.project,
          toInsert.size(),
          toRemove.size());
    } catch (AdoAuthException e) {
      LOG.warn("ADO discovery abortada no login: {}", e.getMessage());
      job.fail("login não concluído: " + e.getMessage());
    } catch (RuntimeException e) {
      LOG.error("ADO discovery falhou: {}", e.getMessage(), e);
      job.fail("falha na descoberta: " + e.getMessage());
    }
  }

  private List<Repository> repositoriesFor(String organization, String project) {
    return repositories.repositories().stream()
        .filter(
            r ->
                r.organization().equalsIgnoreCase(organization)
                    && r.project().equalsIgnoreCase(project))
        .toList();
  }

  private static String lower(String s) {
    return s.toLowerCase(Locale.ROOT);
  }

  /**
   * Mutable in-memory job state; snapshotted into an immutable {@link RepositoryDiscoveryStatus}.
   */
  private static final class Job {
    private final String organization;
    private final String project;
    private volatile String phase = "awaiting-login";
    private volatile boolean done;
    private volatile boolean failed;
    private volatile boolean applied;
    private volatile String message = "";
    private volatile List<DiscoveredRepository> toInsert = List.of();
    private volatile List<String> toRemove = List.of();

    Job(String organization, String project) {
      this.organization = organization;
      this.project = project;
    }

    void finish(List<DiscoveredRepository> insert, List<String> remove) {
      phase = "done";
      done = true;
      toInsert = insert;
      toRemove = remove;
      message = insert.size() + " repositório(s) a inserir, " + remove.size() + " a remover";
    }

    void fail(String why) {
      phase = "failed";
      failed = true;
      done = true;
      message = why;
    }

    RepositoryDiscoveryStatus snapshot(String sessionId) {
      return new RepositoryDiscoveryStatus(
          sessionId, phase, done, failed, message, toInsert, toRemove, applied);
    }
  }
}
