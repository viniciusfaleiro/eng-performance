package com.engperf.adapter.inbound.web.meta;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** The deploy check: {@code /api/version} answers with or without build info, and never 401s. */
class VersionApiTest {

  private static MockMvc mvc(BuildProperties build) {
    return MockMvcBuilders.standaloneSetup(new VersionController(provider(build))).build();
  }

  @Test
  void reportsTheBuildItWasCompiledFrom() throws Exception {
    Properties props = new Properties();
    props.setProperty("version", "0.1.0-SNAPSHOT");
    props.setProperty("commit", "abc1234");
    props.setProperty("time", Long.toString(Instant.parse("2026-09-14T10:00:00Z").toEpochMilli()));

    mvc(new BuildProperties(props))
        .perform(get("/api/version"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value("0.1.0-SNAPSHOT"))
        .andExpect(jsonPath("$.commit").value("abc1234"))
        .andExpect(jsonPath("$.builtAt").value("2026-09-14T10:00:00Z"));
  }

  /**
   * Running from an IDE or a test there is no {@code build-info.properties}. The endpoint has to
   * degrade rather than fail — an endpoint that only works in production cannot be trusted to tell
   * you what production is running.
   */
  @Test
  void degradesToDevWithoutBuildInfo() throws Exception {
    mvc(null)
        .perform(get("/api/version"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value("dev"))
        .andExpect(jsonPath("$.commit").value("dev"));
  }

  private static ObjectProvider<BuildProperties> provider(BuildProperties build) {
    return new ObjectProvider<>() {
      @Override
      public BuildProperties getIfAvailable() {
        return build;
      }

      @Override
      public BuildProperties getObject() {
        return build;
      }

      @Override
      public BuildProperties getObject(Object... args) {
        return build;
      }

      @Override
      public BuildProperties getIfUnique() {
        return build;
      }
    };
  }
}
