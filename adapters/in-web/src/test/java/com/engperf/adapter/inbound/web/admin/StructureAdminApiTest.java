package com.engperf.adapter.inbound.web.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.engperf.adapter.inbound.web.auth.AuthWeb;
import com.engperf.application.auth.AuthenticatedUser;
import com.engperf.application.port.outbound.StructureRepositoryPort;
import com.engperf.application.structure.IdentityService;
import com.engperf.application.structure.RepositoryService;
import com.engperf.application.structure.StructureService;
import com.engperf.domain.access.AccessScope;
import com.engperf.domain.account.AccountStatus;
import com.engperf.domain.account.Role;
import com.engperf.domain.account.UserAccount;
import com.engperf.domain.structure.CommitterIdentity;
import com.engperf.domain.structure.Person;
import com.engperf.domain.structure.Repository;
import com.engperf.domain.structure.Team;
import com.engperf.domain.structure.Vertical;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StructureAdminApiTest {

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    FakeRepo repo = new FakeRepo();
    StructureService structure = new StructureService(repo);
    RepositoryService repositories = new RepositoryService(repo);
    IdentityService identities = new IdentityService(repo);

    structure.createVertical("Pagamentos", null);
    structure.createTeam("Checkout", "v:pagamentos", null);
    structure.createTeam("Antifraude", "v:pagamentos", null);
    structure.createPerson("Ana Souza", null, "t:checkout", LocalDate.of(2026, 1, 1));
    repo.saveRepository(new Repository("checkout-service", "org", "Pagamentos", null, null));
    repo.saveIdentity(new CommitterIdentity("ana@x.com", "Ana", null, 50));
    repo.saveIdentity(new CommitterIdentity("bot@ci", "bot", null, 50));

    ObjectMapper mapper =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    mvc =
        MockMvcBuilders.standaloneSetup(
                new StructureController(structure),
                new RepositoryController(repositories),
                new IdentityController(identities))
            .setControllerAdvice(new AdminExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
            .build();
  }

  @Test
  void treeReturnsRoot() throws Exception {
    mvc.perform(get("/api/structure/tree").requestAttr(AuthWeb.USER, admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("all"))
        .andExpect(jsonPath("$.children[0].id").value("v:pagamentos"));
  }

  private static AuthenticatedUser admin() {
    UserAccount account =
        new UserAccount(
            "u:admin", "Admin", "admin@x.com", Role.ADMIN, AccountStatus.ACTIVE, null, "h:pw");
    AccessScope scope = new AccessScope(true, true, Set.of(), Set.of(), Set.of());
    return new AuthenticatedUser(account, scope);
  }

  @Test
  void createVerticalReturns201() throws Exception {
    mvc.perform(
            post("/api/admin/verticals")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Plataforma\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value("v:plataforma"));
  }

  @Test
  void createTeamWithMissingVerticalReturns422() throws Exception {
    mvc.perform(
            post("/api/admin/teams")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"X\",\"verticalId\":\"v:none\"}"))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void teamChangeMovesPerson() throws Exception {
    mvc.perform(
            post("/api/admin/people/p:ana-souza/team-change")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"teamId\":\"t:antifraude\",\"effectiveDate\":\"2026-07-01\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.teamId").value("t:antifraude"))
        .andExpect(jsonPath("$.memberships.length()").value(2));
  }

  @Test
  void assignIdentityRaisesCoverage() throws Exception {
    mvc.perform(get("/api/admin/coverage")).andExpect(jsonPath("$.attributedPercent").value(0.0));
    mvc.perform(
            post("/api/admin/ado/committers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identity\":\"ana@x.com\",\"personId\":\"p:ana-souza\"}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/admin/coverage")).andExpect(jsonPath("$.attributedPercent").value(50.0));
  }

  @Test
  void assignUnknownIdentityReturns404() throws Exception {
    mvc.perform(
            post("/api/admin/ado/committers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identity\":\"ghost@x.com\",\"personId\":\"p:ana-souza\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void mapRepositoryToTeam() throws Exception {
    mvc.perform(
            put("/api/admin/repositories/checkout-service/team")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"teamId\":\"t:checkout\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.teamId").value("t:checkout"));
  }

  /** Minimal in-memory port for the web tests. */
  private static final class FakeRepo implements StructureRepositoryPort {
    private final Map<String, Vertical> verticals = new LinkedHashMap<>();
    private final Map<String, Team> teams = new LinkedHashMap<>();
    private final Map<String, Person> people = new LinkedHashMap<>();
    private final Map<String, Repository> repositories = new LinkedHashMap<>();
    private final Map<String, CommitterIdentity> identities = new LinkedHashMap<>();

    @Override
    public Vertical saveVertical(Vertical v) {
      verticals.put(v.id(), v);
      return v;
    }

    @Override
    public List<Vertical> findVerticals() {
      return new ArrayList<>(verticals.values());
    }

    @Override
    public Optional<Vertical> findVertical(String id) {
      return Optional.ofNullable(verticals.get(id));
    }

    @Override
    public void deleteVertical(String id) {
      verticals.remove(id);
    }

    @Override
    public Team saveTeam(Team t) {
      teams.put(t.id(), t);
      return t;
    }

    @Override
    public List<Team> findTeams() {
      return new ArrayList<>(teams.values());
    }

    @Override
    public Optional<Team> findTeam(String id) {
      return Optional.ofNullable(teams.get(id));
    }

    @Override
    public void deleteTeam(String id) {
      teams.remove(id);
    }

    @Override
    public Person savePerson(Person p) {
      people.put(p.id(), p);
      return p;
    }

    @Override
    public List<Person> findPeople() {
      return new ArrayList<>(people.values());
    }

    @Override
    public Optional<Person> findPerson(String id) {
      return Optional.ofNullable(people.get(id));
    }

    @Override
    public void deletePerson(String id) {
      people.remove(id);
    }

    @Override
    public Repository saveRepository(Repository r) {
      repositories.put(r.key(), r);
      return r;
    }

    @Override
    public List<Repository> findRepositories() {
      return new ArrayList<>(repositories.values());
    }

    @Override
    public Optional<Repository> findRepository(String key) {
      return Optional.ofNullable(repositories.get(key));
    }

    @Override
    public void deleteRepository(String key) {}

    @Override
    public CommitterIdentity saveIdentity(CommitterIdentity c) {
      identities.put(c.identity(), c);
      return c;
    }

    @Override
    public List<CommitterIdentity> findIdentities() {
      return new ArrayList<>(identities.values());
    }

    @Override
    public Optional<CommitterIdentity> findIdentity(String identity) {
      return Optional.ofNullable(identities.get(identity));
    }
  }
}
