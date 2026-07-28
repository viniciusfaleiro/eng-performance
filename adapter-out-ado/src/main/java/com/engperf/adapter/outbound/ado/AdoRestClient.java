package com.engperf.adapter.outbound.ado;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Minimal Azure DevOps REST reader: an authenticated GET (and paged list) returning parsed JSON.
 */
interface AdoRestClient {

  /** GETs {@code url} with the bearer token and returns the parsed JSON body. */
  JsonNode get(String url, String token);

  /** POSTs {@code jsonBody} to {@code url} (used for WIQL work-item queries). */
  JsonNode post(String url, String token, String jsonBody);
}
