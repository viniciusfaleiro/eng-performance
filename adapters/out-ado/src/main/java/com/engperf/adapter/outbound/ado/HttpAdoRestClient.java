package com.engperf.adapter.outbound.ado;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link AdoRestClient} over the JDK HTTP client; the token is a user's short-lived Entra token.
 */
final class HttpAdoRestClient implements AdoRestClient {

  private static final Logger LOG = LoggerFactory.getLogger(HttpAdoRestClient.class);
  // Cap the ADO error body we surface/log so a stray HTML page can't flood the message.
  private static final int MAX_BODY = 600;

  private final HttpClient http;
  private final ObjectMapper json = new ObjectMapper();

  HttpAdoRestClient() {
    this(HttpClient.newHttpClient());
  }

  HttpAdoRestClient(HttpClient http) {
    this.http = http;
  }

  @Override
  public JsonNode get(String url, String token) {
    return send(HttpRequest.newBuilder(URI.create(url)).GET(), "GET", token, url);
  }

  @Override
  public JsonNode post(String url, String token, String jsonBody) {
    return send(
        HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody)),
        "POST",
        token,
        url);
  }

  private JsonNode send(HttpRequest.Builder builder, String method, String token, String url) {
    // Never log the token or the Authorization header — only the method + URL.
    LOG.debug("ADO {} {}", method, url);
    try {
      HttpRequest request =
          builder
              .header("Authorization", "Bearer " + token)
              .header("Accept", "application/json")
              .build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      int status = response.statusCode();
      if (status / 100 != 2) {
        String detail = describe(status, response.body());
        // The ADO response body carries the real cause (e.g. TF400813, project not found) — keep
        // it.
        LOG.warn("ADO {} {} failed: {}", method, url, detail);
        throw new IllegalStateException("Azure DevOps " + url + " -> " + detail);
      }
      LOG.debug("ADO {} {} -> HTTP {}", method, url, status);
      return json.readTree(response.body());
    } catch (IOException e) {
      LOG.warn("Network error calling Azure DevOps {}: {}", url, e.toString());
      throw new IllegalStateException(
          "network error calling Azure DevOps " + url + ": " + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted calling Azure DevOps " + url, e);
    }
  }

  /** {@code HTTP <status>: <message>}, mapping the common auth/scope codes to a human hint. */
  private String describe(int status, String body) {
    String hint =
        switch (status) {
          case 401 -> "não autenticado (token inválido/expirado — refaça o login)";
          case 403 -> "sem permissão (a conta não tem acesso a esta organização/projeto)";
          case 404 -> "não encontrado (organização, projeto ou repositório incorreto)";
          case 203 -> "resposta de login (a org exige autenticação/consentimento adicional)";
          default -> null;
        };
    String message = message(body);
    StringBuilder sb = new StringBuilder("HTTP ").append(status);
    if (hint != null) {
      sb.append(' ').append(hint);
    }
    if (!message.isBlank()) {
      sb.append(" — ").append(message);
    }
    return sb.toString();
  }

  /** Pull ADO's {@code message} field out of the JSON error body, else a trimmed snippet. */
  private String message(String body) {
    if (body == null || body.isBlank()) {
      return "";
    }
    try {
      JsonNode node = json.readTree(body);
      String message = node.path("message").asText("");
      if (!message.isBlank()) {
        return truncate(message);
      }
    } catch (IOException ignored) {
      // not JSON (often an HTML sign-in page) — fall back to a raw snippet
    }
    return truncate(body.replaceAll("\\s+", " ").trim());
  }

  private static String truncate(String s) {
    return s.length() <= MAX_BODY ? s : s.substring(0, MAX_BODY) + "…";
  }
}
