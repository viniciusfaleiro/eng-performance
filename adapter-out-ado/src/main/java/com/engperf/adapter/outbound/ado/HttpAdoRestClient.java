package com.engperf.adapter.outbound.ado;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * {@link AdoRestClient} over the JDK HTTP client; the token is a user's short-lived Entra token.
 */
final class HttpAdoRestClient implements AdoRestClient {

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
    return send(HttpRequest.newBuilder(URI.create(url)).GET(), token, url);
  }

  @Override
  public JsonNode post(String url, String token, String jsonBody) {
    return send(
        HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody)),
        token,
        url);
  }

  private JsonNode send(HttpRequest.Builder builder, String token, String url) {
    try {
      HttpRequest request =
          builder
              .header("Authorization", "Bearer " + token)
              .header("Accept", "application/json")
              .build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 != 2) {
        throw new IllegalStateException(
            "Azure DevOps " + url + " -> HTTP " + response.statusCode());
      }
      return json.readTree(response.body());
    } catch (IOException e) {
      throw new IllegalStateException("network error calling Azure DevOps: " + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted calling Azure DevOps", e);
    }
  }
}
