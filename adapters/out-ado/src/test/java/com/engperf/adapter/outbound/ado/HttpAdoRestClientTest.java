package com.engperf.adapter.outbound.ado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

/** The client surfaces Azure DevOps' own error detail so a failed load is diagnosable. */
class HttpAdoRestClientTest {

  @SuppressWarnings("unchecked")
  private static HttpClient clientReturning(int status, String body) throws Exception {
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(status);
    when(response.body()).thenReturn(body);
    HttpClient http = mock(HttpClient.class);
    when(http.<String>send(any(), any())).thenReturn(response);
    return http;
  }

  @Test
  void nonSuccessErrorCarriesStatusHintAndAdoMessage() throws Exception {
    HttpClient http =
        clientReturning(
            404, "{\"message\":\"TF401019: The Git repository with name 'x' does not exist\"}");
    HttpAdoRestClient client = new HttpAdoRestClient(http);

    assertThatThrownBy(() -> client.get("https://dev.azure.com/org/proj/_apis/git", "tok"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("HTTP 404")
        .hasMessageContaining("não encontrado") // human hint for the status
        .hasMessageContaining("TF401019"); // ADO's own error detail
  }

  @Test
  void unauthorizedHintsAtReLogin() throws Exception {
    HttpAdoRestClient client = new HttpAdoRestClient(clientReturning(401, ""));

    assertThatThrownBy(() -> client.get("https://dev.azure.com/org/_apis/git", "tok"))
        .hasMessageContaining("HTTP 401")
        .hasMessageContaining("refaça o login");
  }

  @Test
  void successParsesJsonBody() throws Exception {
    HttpAdoRestClient client = new HttpAdoRestClient(clientReturning(200, "{\"value\":[1,2,3]}"));

    JsonNode node = client.get("https://dev.azure.com/org/_apis/git", "tok");

    assertThat(node.path("value")).hasSize(3);
  }
}
