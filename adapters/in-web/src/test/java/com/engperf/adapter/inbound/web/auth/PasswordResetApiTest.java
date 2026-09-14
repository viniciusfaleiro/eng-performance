package com.engperf.adapter.inbound.web.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.engperf.application.auth.AuthPrincipal;
import com.engperf.application.auth.AuthService;
import com.engperf.application.auth.AuthorizationService;
import com.engperf.application.auth.PasswordResetService;
import com.engperf.application.port.outbound.PasswordHasher;
import com.engperf.application.port.outbound.PasswordResetTokenPort;
import com.engperf.application.port.outbound.PlatformConfigPort;
import com.engperf.application.port.outbound.SecureTokenGenerator;
import com.engperf.application.port.outbound.StructureRepositoryPort;
import com.engperf.application.port.outbound.TokenService;
import com.engperf.application.port.outbound.UserAccountRepositoryPort;
import com.engperf.domain.account.AccountStatus;
import com.engperf.domain.account.PasswordResetToken;
import com.engperf.domain.account.Role;
import com.engperf.domain.account.UserAccount;
import com.engperf.domain.config.AdoIntegration;
import com.engperf.domain.config.AiConvention;
import com.engperf.domain.config.AiStrategy;
import com.engperf.domain.config.MailTransport;
import com.engperf.domain.config.SmtpSettings;
import com.engperf.domain.structure.CommitterIdentity;
import com.engperf.domain.structure.Person;
import com.engperf.domain.structure.Repository;
import com.engperf.domain.structure.Team;
import com.engperf.domain.structure.Vertical;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The reset endpoints respond identically regardless of the email, and the public routes work
 * without a token.
 */
class PasswordResetApiTest {

  private static final PasswordHasher HASHER =
      new PasswordHasher() {
        @Override
        public String hash(String raw) {
          return "h:" + raw;
        }

        @Override
        public boolean matches(String raw, String hash) {
          return hash != null && hash.equals("h:" + raw);
        }
      };

  private static final SecureTokenGenerator TOKEN_GENERATOR =
      new SecureTokenGenerator() {
        @Override
        public String generate() {
          return "raw-token";
        }

        @Override
        public String hash(String rawToken) {
          return "hash:" + rawToken;
        }
      };

  private FakeAccounts accounts;
  private FakeTokens resetTokens;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    accounts = new FakeAccounts();
    accounts.save(
        new UserAccount(
            "u:ana", "Ana", "ana@x.com", Role.MANAGER, AccountStatus.ACTIVE, null, "h:secret"));
    accounts.save(
        new UserAccount(
            "u:ex", "Ex", "ex@x.com", Role.CONTRIBUTOR, AccountStatus.DISABLED, null, "h:secret"));
    resetTokens = new FakeTokens();
    Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    PasswordResetService passwordReset =
        new PasswordResetService(
            accounts,
            resetTokens,
            HASHER,
            TOKEN_GENERATOR,
            (to, subject, body) -> {},
            new FakeConfig(),
            clock);
    AuthService authService = new AuthService(accounts, HASHER, new FakeSessionTokens());
    AuthorizationService authz = new AuthorizationService(accounts, new FakeStructure());

    mvc =
        MockMvcBuilders.standaloneSetup(new AuthController(authService, authz, passwordReset))
            .setControllerAdvice(new AuthWebExceptionHandler())
            .addFilter(new AuthTokenFilter(new FakeSessionTokens(), authz), "/api/*")
            .build();
  }

  @Test
  void requestRespondsTheSameForKnownUnknownAndDisabledEmails() throws Exception {
    mvc.perform(
            post("/api/auth/password-reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ana@x.com\"}"))
        .andExpect(status().isAccepted());
    mvc.perform(
            post("/api/auth/password-reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@x.com\"}"))
        .andExpect(status().isAccepted());
    mvc.perform(
            post("/api/auth/password-reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ex@x.com\"}"))
        .andExpect(status().isAccepted());

    // Only the known, active account actually issued a token.
    assertThat(resetTokens.all()).hasSize(1);
  }

  @Test
  void publicRoutesWorkWithoutAToken() throws Exception {
    mvc.perform(
            post("/api/auth/password-reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ana@x.com\"}"))
        .andExpect(status().isAccepted());

    mvc.perform(
            post("/api/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"raw-token\",\"newPassword\":\"novaSenha1\"}"))
        .andExpect(status().isNoContent());
  }

  @Test
  void confirmRejectsUnknownTokenWith400() throws Exception {
    mvc.perform(
            post("/api/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"never-issued\",\"newPassword\":\"novaSenha1\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void confirmRejectsBlankPasswordWith400() throws Exception {
    mvc.perform(
            post("/api/auth/password-reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ana@x.com\"}"))
        .andExpect(status().isAccepted());

    mvc.perform(
            post("/api/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"raw-token\",\"newPassword\":\" \"}"))
        .andExpect(status().isBadRequest());
  }

  private static final class FakeSessionTokens implements TokenService {
    @Override
    public String issue(AuthPrincipal principal) {
      return "tok:" + principal.accountId();
    }

    @Override
    public Optional<AuthPrincipal> verify(String token) {
      return Optional.empty();
    }
  }

  private static final class FakeAccounts implements UserAccountRepositoryPort {
    private final Map<String, UserAccount> byId = new LinkedHashMap<>();

    @Override
    public UserAccount save(UserAccount a) {
      byId.put(a.id(), a);
      return a;
    }

    @Override
    public List<UserAccount> findAll() {
      return new ArrayList<>(byId.values());
    }

    @Override
    public Optional<UserAccount> findById(String id) {
      return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<UserAccount> findByEmail(String email) {
      return byId.values().stream().filter(a -> a.email().equals(email)).findFirst();
    }

    @Override
    public void deleteById(String id) {
      byId.remove(id);
    }
  }

  private static final class FakeTokens implements PasswordResetTokenPort {
    private final Map<String, PasswordResetToken> byId = new LinkedHashMap<>();

    List<PasswordResetToken> all() {
      return new ArrayList<>(byId.values());
    }

    @Override
    public PasswordResetToken save(PasswordResetToken token) {
      byId.put(token.id(), token);
      return token;
    }

    @Override
    public Optional<PasswordResetToken> findByTokenHash(String tokenHash) {
      return byId.values().stream().filter(t -> t.tokenHash().equals(tokenHash)).findFirst();
    }

    @Override
    public List<PasswordResetToken> findPendingByAccount(String accountId) {
      return byId.values().stream()
          .filter(t -> t.accountId().equals(accountId) && !t.isConsumed())
          .toList();
    }

    @Override
    public void consumeAllPending(String accountId, Instant at) {
      for (PasswordResetToken t : findPendingByAccount(accountId)) {
        byId.put(t.id(), t.consumedAt(at));
      }
    }

    @Override
    public long countIssuedSince(String accountId, Instant since) {
      return byId.values().stream()
          .filter(t -> t.accountId().equals(accountId) && !t.createdAt().isBefore(since))
          .count();
    }
  }

  private static final class FakeConfig implements PlatformConfigPort {
    @Override
    public AdoIntegration getAdoIntegration() {
      return new AdoIntegration(false, null);
    }

    @Override
    public AdoIntegration saveAdoIntegration(AdoIntegration integration) {
      return integration;
    }

    @Override
    public AiConvention getAiConvention() {
      return new AiConvention(AiStrategy.TRAILER, null, null, null, false);
    }

    @Override
    public AiConvention saveAiConvention(AiConvention convention) {
      return convention;
    }

    @Override
    public SmtpSettings getSmtpSettings() {
      return new SmtpSettings(
          false, null, null, MailTransport.NONE, null, null, null, null, "https://app.example.com");
    }

    @Override
    public SmtpSettings saveSmtpSettings(SmtpSettings settings) {
      return settings;
    }
  }

  /** Empty structure — RBAC derivation itself is covered elsewhere. */
  private static final class FakeStructure implements StructureRepositoryPort {
    @Override
    public Vertical saveVertical(Vertical v) {
      return v;
    }

    @Override
    public List<Vertical> findVerticals() {
      return List.of();
    }

    @Override
    public Optional<Vertical> findVertical(String id) {
      return Optional.empty();
    }

    @Override
    public void deleteVertical(String id) {}

    @Override
    public Team saveTeam(Team t) {
      return t;
    }

    @Override
    public List<Team> findTeams() {
      return List.of();
    }

    @Override
    public Optional<Team> findTeam(String id) {
      return Optional.empty();
    }

    @Override
    public void deleteTeam(String id) {}

    @Override
    public Person savePerson(Person p) {
      return p;
    }

    @Override
    public List<Person> findPeople() {
      return List.of();
    }

    @Override
    public Optional<Person> findPerson(String id) {
      return Optional.empty();
    }

    @Override
    public void deletePerson(String id) {}

    @Override
    public Repository saveRepository(Repository r) {
      return r;
    }

    @Override
    public List<Repository> findRepositories() {
      return List.of();
    }

    @Override
    public Optional<Repository> findRepository(String key) {
      return Optional.empty();
    }

    @Override
    public void deleteRepository(String key) {}

    @Override
    public CommitterIdentity saveIdentity(CommitterIdentity c) {
      return c;
    }

    @Override
    public List<CommitterIdentity> findIdentities() {
      return List.of();
    }

    @Override
    public Optional<CommitterIdentity> findIdentity(String identity) {
      return Optional.empty();
    }
  }
}
