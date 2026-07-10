package com.engperf.adapter.inbound.web.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.engperf.application.account.UserAccountService;
import com.engperf.application.config.PlatformConfigService;
import com.engperf.application.port.outbound.PlatformConfigPort;
import com.engperf.application.port.outbound.UserAccountRepositoryPort;
import com.engperf.domain.account.UserAccount;
import com.engperf.domain.config.AdoIntegration;
import com.engperf.domain.config.AiConvention;
import com.engperf.domain.config.AiStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminAccountsApiTest {

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    var accountRepo = new FakeAccounts();
    var configPort = new FakeConfig();
    var userService = new UserAccountService(accountRepo, raw -> "h:" + raw);
    var configService = new PlatformConfigService(configPort);

    ObjectMapper mapper =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    mvc =
        MockMvcBuilders.standaloneSetup(
                new UserController(userService), new ConfigController(configService))
            .setControllerAdvice(new AdminExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
            .build();
  }

  @Test
  void createUserReturns201WithLowercaseRole() throws Exception {
    mvc.perform(
            post("/api/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"Ana\",\"email\":\"ana@x.com\",\"password\":\"pw12345678\","
                        + "\"role\":\"manager\",\"status\":\"active\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.role").value("manager"))
        .andExpect(jsonPath("$.status").value("active"));
  }

  @Test
  void duplicateEmailReturns409AndInvalidRole422() throws Exception {
    String body =
        "{\"name\":\"Ana\",\"email\":\"ana@x.com\",\"password\":\"pw12345678\",\"role\":\"admin\"}";
    mvc.perform(post("/api/admin/users").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated());
    mvc.perform(post("/api/admin/users").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isConflict());
    mvc.perform(
            post("/api/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"B\",\"email\":\"b@x.com\",\"password\":\"pw12345678\",\"role\":\"wizard\"}"))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void updatePasswordAndDelete() throws Exception {
    mvc.perform(
            post("/api/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"Ana\",\"email\":\"ana@x.com\","
                        + "\"password\":\"pw12345678\",\"role\":\"admin\"}"))
        .andExpect(status().isCreated());
    String id = "u:ana-x-com";
    mvc.perform(
            put("/api/admin/users/" + id + "/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"newpass99\"}"))
        .andExpect(status().isNoContent());
    mvc.perform(delete("/api/admin/users/" + id)).andExpect(status().isNoContent());
  }

  @Test
  void adoConfigRedactsPatAndTestsConnection() throws Exception {
    mvc.perform(
            put("/api/admin/integrations/azure-devops")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"organizationUrl\":\"https://dev.azure.com/org\","
                        + "\"personalAccessToken\":\"pat\",\"productionStageRule\":\"prod\"}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/admin/integrations/azure-devops"))
        .andExpect(jsonPath("$.organizationUrl").value("https://dev.azure.com/org"))
        .andExpect(jsonPath("$.patRedacted").isNotEmpty());
    mvc.perform(post("/api/admin/integrations/azure-devops/test"))
        .andExpect(jsonPath("$.connected").value(true));
  }

  @Test
  void aiConventionSavesAndReads() throws Exception {
    mvc.perform(
            put("/api/admin/ai-convention")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"strategy\":\"tag\",\"tag\":\"[ai]\",\"regex\":\"(?i)\\\\[ai\\\\]\",\"caseSensitive\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.strategy").value("tag"))
        .andExpect(jsonPath("$.tag").value("[ai]"));
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

  private static final class FakeConfig implements PlatformConfigPort {
    private AdoIntegration ado = new AdoIntegration(null, null, null, false, null);
    private AiConvention ai = new AiConvention(AiStrategy.TRAILER, null, null, null, false);

    @Override
    public AdoIntegration getAdoIntegration() {
      return ado;
    }

    @Override
    public AdoIntegration saveAdoIntegration(AdoIntegration integration) {
      this.ado = integration;
      return integration;
    }

    @Override
    public AiConvention getAiConvention() {
      return ai;
    }

    @Override
    public AiConvention saveAiConvention(AiConvention convention) {
      this.ai = convention;
      return convention;
    }
  }
}
