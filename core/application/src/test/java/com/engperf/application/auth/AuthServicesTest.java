package com.engperf.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.engperf.application.port.outbound.PasswordHasher;
import com.engperf.application.port.outbound.StructureRepositoryPort;
import com.engperf.application.port.outbound.TokenService;
import com.engperf.application.port.outbound.UserAccountRepositoryPort;
import com.engperf.domain.account.AccountStatus;
import com.engperf.domain.account.Role;
import com.engperf.domain.account.UserAccount;
import com.engperf.domain.structure.CommitterIdentity;
import com.engperf.domain.structure.Person;
import com.engperf.domain.structure.Repository;
import com.engperf.domain.structure.Team;
import com.engperf.domain.structure.Vertical;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthServicesTest {

  private FakeAccounts accounts;
  private AuthService auth;
  private AuthorizationService authz;

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

  private static final TokenService TOKENS =
      new TokenService() {
        @Override
        public String issue(AuthPrincipal p) {
          return "tok:" + p.accountId();
        }

        @Override
        public Optional<AuthPrincipal> verify(String token) {
          return Optional.empty();
        }
      };

  @BeforeEach
  void setUp() {
    accounts = new FakeAccounts();
    auth = new AuthService(accounts, HASHER, TOKENS);
    authz = new AuthorizationService(accounts, new FakeStructure());
    accounts.save(
        new UserAccount(
            "u:ana", "Ana", "ana@x.com", Role.MANAGER, AccountStatus.ACTIVE, "p:ana", "h:secret"));
    accounts.save(
        new UserAccount(
            "u:ex", "Ex", "ex@x.com", Role.CONTRIBUTOR, AccountStatus.DISABLED, null, "h:secret"));
  }

  @Test
  void loginSucceedsWithValidCredentials() {
    LoginResult r = auth.login("ANA@x.com", "secret");
    assertThat(r.token()).isEqualTo("tok:u:ana");
    assertThat(r.principal().role()).isEqualTo(Role.MANAGER);
    assertThat(r.principal().personId()).isEqualTo("p:ana");
  }

  @Test
  void loginRejectsWrongPasswordUnknownEmailAndDisabled() {
    assertThatExceptionOfType(AuthenticationException.class)
        .isThrownBy(() -> auth.login("ana@x.com", "wrong"));
    assertThatExceptionOfType(AuthenticationException.class)
        .isThrownBy(() -> auth.login("nobody@x.com", "secret"));
    assertThatExceptionOfType(AuthenticationException.class)
        .isThrownBy(() -> auth.login("ex@x.com", "secret"));
  }

  @Test
  void changePasswordVerifiesCurrentAndUpdatesHash() {
    auth.changePassword("u:ana", "secret", "novasenha");
    assertThat(accounts.findById("u:ana"))
        .get()
        .extracting(UserAccount::passwordHash)
        .isEqualTo("h:novasenha");
    assertThatExceptionOfType(AuthenticationException.class)
        .isThrownBy(() -> auth.changePassword("u:ana", "wrong-current", "x"));
  }

  @Test
  void scopeResolvesAdminAndMember() {
    accounts.save(
        new UserAccount(
            "u:adm", "Adm", "adm@x.com", Role.ADMIN, AccountStatus.ACTIVE, null, "h:secret"));
    assertThat(
            authz.scopeOf(new AuthPrincipal("u:adm", "adm@x.com", Role.ADMIN, null)).canConfigure())
        .isTrue();
    assertThat(
            authz
                .scopeOf(new AuthPrincipal("u:ana", "ana@x.com", Role.MANAGER, "p:ana"))
                .canViewIndividual("p:ana"))
        .isTrue();
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

  /** Empty structure — the derivation itself is covered by AccessPolicyTest. */
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
      return List.of(Person.create("p:ana", "Ana", null, "t:checkout", LocalDate.of(2026, 1, 1)));
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
