package com.engperf.adapter.inbound.web.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.engperf.adapter.inbound.web.admin.AdminExceptionHandler;
import com.engperf.adapter.inbound.web.admin.StructureController;
import com.engperf.application.auth.AuthPrincipal;
import com.engperf.application.auth.AuthService;
import com.engperf.application.auth.AuthorizationService;
import com.engperf.application.port.outbound.PasswordHasher;
import com.engperf.application.port.outbound.StructureRepositoryPort;
import com.engperf.application.port.outbound.TokenService;
import com.engperf.application.port.outbound.UserAccountRepositoryPort;
import com.engperf.application.structure.StructureService;
import com.engperf.domain.account.AccountStatus;
import com.engperf.domain.account.Role;
import com.engperf.domain.account.UserAccount;
import com.engperf.domain.structure.CommitterIdentity;
import com.engperf.domain.structure.Person;
import com.engperf.domain.structure.Repository;
import com.engperf.domain.structure.Team;
import com.engperf.domain.structure.Vertical;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Login (401), the admin gate (403), tree scoping, and coaching-only node access (403). */
class AuthApiTest {

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

  private final ObjectMapper mapper = new ObjectMapper();
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    FakeStructure structure = new FakeStructure();
    // v:eng ── t:checkout (manager p:ana) ── p:ana, p:bruno
    //       └─ t:payments (manager p:carla) ── p:carla
    structure.verticals.add(new Vertical("v:eng", "Engenharia", null));
    structure.teams.add(new Team("t:checkout", "Checkout", "v:eng", "p:ana", null));
    structure.teams.add(new Team("t:payments", "Payments", "v:eng", "p:carla", null));
    LocalDate d = LocalDate.of(2026, 1, 1);
    structure.people.add(Person.create("p:ana", "Ana", null, "t:checkout", d));
    structure.people.add(Person.create("p:bruno", "Bruno", null, "t:checkout", d));
    structure.people.add(Person.create("p:carla", "Carla", null, "t:payments", d));

    FakeAccounts accounts = new FakeAccounts();
    accounts.save(acct("u:admin", "admin@x.com", Role.ADMIN, null));
    accounts.save(acct("u:ana", "ana@x.com", Role.MANAGER, "p:ana"));
    accounts.save(acct("u:bruno", "bruno@x.com", Role.CONTRIBUTOR, "p:bruno"));

    TokenService tokens = new FakeTokens();
    AuthService authService = new AuthService(accounts, HASHER, tokens);
    AuthorizationService authz = new AuthorizationService(accounts, structure);
    StructureService structureService = new StructureService(structure);

    mvc =
        MockMvcBuilders.standaloneSetup(
                new AuthController(authService, authz), new StructureController(structureService))
            .setControllerAdvice(new AuthWebExceptionHandler(), new AdminExceptionHandler())
            .addFilter(new AuthTokenFilter(tokens, authz), "/api/*")
            .build();
  }

  private static UserAccount acct(String id, String email, Role role, String personId) {
    return new UserAccount(id, id, email, role, AccountStatus.ACTIVE, personId, "h:pw");
  }

  private String login(String email) throws Exception {
    String body =
        mvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + email + "\",\"password\":\"pw\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readTree(body).get("token").asText();
  }

  private static HttpHeaders bearer(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return headers;
  }

  @Test
  void loginSucceedsAndReportsAdminFlag() throws Exception {
    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@x.com\",\"password\":\"pw\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").isNotEmpty())
        .andExpect(jsonPath("$.user.admin").value(true))
        .andExpect(jsonPath("$.user.role").value("admin"));
  }

  @Test
  void loginWithWrongPasswordReturns401() throws Exception {
    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@x.com\",\"password\":\"nope\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void protectedRouteWithoutTokenReturns401() throws Exception {
    mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/structure/tree")).andExpect(status().isUnauthorized());
  }

  @Test
  void adminReachesConfigButMemberIsForbidden() throws Exception {
    mvc.perform(get("/api/admin/verticals").headers(bearer(login("admin@x.com"))))
        .andExpect(status().isOk());
    mvc.perform(get("/api/admin/verticals").headers(bearer(login("bruno@x.com"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void memberIsBarredFromAnotherTeamButSeesOwn() throws Exception {
    HttpHeaders bruno = bearer(login("bruno@x.com"));
    mvc.perform(get("/api/structure/nodes/t:checkout").headers(bruno)).andExpect(status().isOk());
    mvc.perform(get("/api/structure/nodes/t:payments").headers(bruno))
        .andExpect(status().isForbidden());
  }

  @Test
  void coachingOnlyMemberSeesSelfNotPeerAndManagerSeesTeamMember() throws Exception {
    HttpHeaders bruno = bearer(login("bruno@x.com"));
    mvc.perform(get("/api/structure/nodes/p:bruno").headers(bruno)).andExpect(status().isOk());
    mvc.perform(get("/api/structure/nodes/p:ana").headers(bruno)).andExpect(status().isForbidden());

    // The team manager may view an individual on their own team (coaching).
    mvc.perform(get("/api/structure/nodes/p:bruno").headers(bearer(login("ana@x.com"))))
        .andExpect(status().isOk());
  }

  @Test
  void treeIsPrunedToTheMemberScope() throws Exception {
    String body =
        mvc.perform(get("/api/structure/tree").headers(bearer(login("bruno@x.com"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode root = mapper.readTree(body);
    JsonNode verticals = root.get("children");
    // Only Bruno's own team survives, and under it only Bruno himself (peer Ana is pruned).
    org.assertj.core.api.Assertions.assertThat(verticals).hasSize(1);
    JsonNode teams = verticals.get(0).get("children");
    org.assertj.core.api.Assertions.assertThat(teams).hasSize(1);
    org.assertj.core.api.Assertions.assertThat(teams.get(0).get("id").asText())
        .isEqualTo("t:checkout");
    JsonNode people = teams.get(0).get("children");
    org.assertj.core.api.Assertions.assertThat(people).hasSize(1);
    org.assertj.core.api.Assertions.assertThat(people.get(0).get("id").asText())
        .isEqualTo("p:bruno");
  }

  /** In-memory token round-trip — keeps the web test independent of the JWT persistence adapter. */
  private static final class FakeTokens implements TokenService {
    private final Map<String, AuthPrincipal> issued = new LinkedHashMap<>();

    @Override
    public String issue(AuthPrincipal principal) {
      String token = "tok:" + principal.accountId();
      issued.put(token, principal);
      return token;
    }

    @Override
    public Optional<AuthPrincipal> verify(String token) {
      return Optional.ofNullable(issued.get(token));
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

  private static final class FakeStructure implements StructureRepositoryPort {
    private final List<Vertical> verticals = new ArrayList<>();
    private final List<Team> teams = new ArrayList<>();
    private final List<Person> people = new ArrayList<>();

    @Override
    public Vertical saveVertical(Vertical v) {
      verticals.add(v);
      return v;
    }

    @Override
    public List<Vertical> findVerticals() {
      return new ArrayList<>(verticals);
    }

    @Override
    public Optional<Vertical> findVertical(String id) {
      return verticals.stream().filter(v -> v.id().equals(id)).findFirst();
    }

    @Override
    public void deleteVertical(String id) {}

    @Override
    public Team saveTeam(Team t) {
      teams.add(t);
      return t;
    }

    @Override
    public List<Team> findTeams() {
      return new ArrayList<>(teams);
    }

    @Override
    public Optional<Team> findTeam(String id) {
      return teams.stream().filter(t -> t.id().equals(id)).findFirst();
    }

    @Override
    public void deleteTeam(String id) {}

    @Override
    public Person savePerson(Person p) {
      people.add(p);
      return p;
    }

    @Override
    public List<Person> findPeople() {
      return new ArrayList<>(people);
    }

    @Override
    public Optional<Person> findPerson(String id) {
      return people.stream().filter(p -> p.id().equals(id)).findFirst();
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
