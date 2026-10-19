package com.engperf.adapter.inbound.web.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.engperf.application.port.inbound.PlatformConfigUseCase;
import com.engperf.domain.config.AdoIntegration;
import com.engperf.domain.config.AiConvention;
import com.engperf.domain.config.AiStrategy;
import com.engperf.domain.config.ImpedimentConvention;
import com.engperf.domain.config.MailTransport;
import com.engperf.domain.config.SmtpSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Convenção de impedimento pela API de admin: o PUT grava e o GET devolve o que foi gravado, e
 * limpar os dois campos é o gesto que desliga o sinal — sem isso não há como voltar atrás numa
 * configuração errada sem deploy.
 */
class ImpedimentConventionApiTest {

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.standaloneSetup(new ConfigController(new FakeConfig())).build();
  }

  @Test
  void readsBackWhatThePutSaved() throws Exception {
    mvc.perform(
            put("/api/admin/impediment-convention")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fieldName\":\"Custom.Impediment\",\"tagPrefix\":\"Impedimento:\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fieldName").value("Custom.Impediment"));

    mvc.perform(get("/api/admin/impediment-convention"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fieldName").value("Custom.Impediment"))
        .andExpect(jsonPath("$.tagPrefix").value("Impedimento:"));
  }

  @Test
  void blankValuesSwitchTheSignalOff() throws Exception {
    mvc.perform(
            put("/api/admin/impediment-convention")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fieldName\":\"  \",\"tagPrefix\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fieldName").doesNotExist())
        .andExpect(jsonPath("$.tagPrefix").doesNotExist());
  }

  private static final class FakeConfig implements PlatformConfigUseCase {
    private ImpedimentConvention impediment = ImpedimentConvention.off();

    @Override
    public ImpedimentConvention impedimentConvention() {
      return impediment;
    }

    @Override
    public ImpedimentConvention saveImpedimentConvention(String fieldName, String tagPrefix) {
      impediment = new ImpedimentConvention(fieldName, tagPrefix);
      return impediment;
    }

    @Override
    public AdoIntegration adoIntegration() {
      return new AdoIntegration(false, null);
    }

    @Override
    public AdoIntegration markAdoConnected() {
      return adoIntegration();
    }

    @Override
    public AiConvention aiConvention() {
      return new AiConvention(AiStrategy.TRAILER, null, null, null, false);
    }

    @Override
    public AiConvention saveAiConvention(
        AiStrategy strategy, String trailer, String tag, String regex, boolean caseSensitive) {
      return aiConvention();
    }

    @Override
    public SmtpSettings smtpSettings() {
      return new SmtpSettings(false, null, null, MailTransport.NONE, null, null, null, null, null);
    }

    @Override
    public SmtpSettings saveSmtpSettings(SmtpSettings settings) {
      return smtpSettings();
    }

    @Override
    public void sendTestEmail(String to) {}
  }
}
