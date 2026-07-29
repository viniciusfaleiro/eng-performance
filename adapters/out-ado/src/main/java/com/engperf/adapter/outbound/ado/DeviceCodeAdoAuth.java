package com.engperf.adapter.outbound.ado;

import com.engperf.application.ado.AdoAuthException;
import com.engperf.application.ado.DeviceCodePrompt;
import com.engperf.application.port.outbound.AdoAuthPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Interactive Microsoft Entra device-code auth for Azure DevOps — the exact flow {@code az login}
 * uses, reproduced with the JDK HTTP client. Reuses the broadly-consented Azure CLI public client,
 * so no PAT and no app registration are needed. The token lives only in the caller's memory.
 */
@Component
public final class DeviceCodeAdoAuth implements AdoAuthPort {

  private static final Logger LOG = LoggerFactory.getLogger(DeviceCodeAdoAuth.class);

  // Public client id of the Azure CLI (pre-consented in most tenants) — no app registration.
  private static final String CLIENT = "04b07795-8ddb-461a-bbee-02f9e1bf7b46";
  // 499b84ac-… is the Azure DevOps resource; .default = already-consented permissions.
  private static final String SCOPE =
      "499b84ac-1321-427f-aa17-267ca6975798/.default offline_access";
  private static final String AUTHORITY =
      "https://login.microsoftonline.com/organizations/oauth2/v2.0";

  private final HttpClient http;
  private final ObjectMapper json = new ObjectMapper();

  public DeviceCodeAdoAuth() {
    this(HttpClient.newHttpClient());
  }

  DeviceCodeAdoAuth(HttpClient http) {
    this.http = http;
  }

  @Override
  public DeviceCodePrompt beginDeviceCode() {
    LOG.info("ADO auth: solicitando device-code ao Entra");
    JsonNode r = post(AUTHORITY + "/devicecode", Map.of("client_id", CLIENT, "scope", SCOPE));
    if (!r.hasNonNull("device_code")) {
      String why = errorOf(r, "tenant blocked the device-code flow");
      LOG.warn("ADO auth: device-code recusado — {}", why);
      throw new AdoAuthException(why);
    }
    LOG.info("ADO auth: device-code emitido, aguardando login do usuário");
    return new DeviceCodePrompt(
        r.get("user_code").asText(),
        r.get("verification_uri").asText(),
        r.get("device_code").asText(),
        r.path("interval").asInt(5),
        r.path("expires_in").asInt(900));
  }

  @Override
  public Optional<String> poll(String deviceCode) {
    JsonNode r =
        post(
            AUTHORITY + "/token",
            Map.of(
                "grant_type", "urn:ietf:params:oauth:grant-type:device_code",
                "client_id", CLIENT,
                "device_code", deviceCode));
    if (r.hasNonNull("access_token")) {
      LOG.info("ADO auth: login concluído, token obtido");
      return Optional.of(r.get("access_token").asText());
    }
    String error = r.path("error").asText("");
    return switch (error) {
      case "authorization_pending", "slow_down" -> Optional.empty();
      default -> {
        String why = errorOf(r, "device-code auth failed");
        LOG.warn("ADO auth: falha no login ({}) — {}", error.isBlank() ? "sem código" : error, why);
        throw new AdoAuthException(why);
      }
    };
  }

  private JsonNode post(String url, Map<String, String> form) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(url))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .POST(HttpRequest.BodyPublishers.ofString(encode(form)))
              .build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      return json.readTree(response.body());
    } catch (IOException e) {
      throw new AdoAuthException("network error talking to Entra: " + e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AdoAuthException("interrupted talking to Entra");
    }
  }

  private static String errorOf(JsonNode r, String fallback) {
    String desc = r.path("error_description").asText("");
    return desc.isBlank() ? fallback : desc.split("\\r?\\n")[0];
  }

  private static String encode(Map<String, String> form) {
    StringBuilder sb = new StringBuilder();
    form.forEach(
        (k, v) -> {
          if (sb.length() > 0) {
            sb.append('&');
          }
          sb.append(URLEncoder.encode(k, StandardCharsets.UTF_8))
              .append('=')
              .append(URLEncoder.encode(v, StandardCharsets.UTF_8));
        });
    return sb.toString();
  }
}
