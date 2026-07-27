package com.engperf.adapter.inbound.web.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/** Builds RFC-7807-shaped problem bodies, both as a Map (advice) and written to a raw response. */
final class Problem {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private Problem() {}

  static Map<String, Object> body(HttpStatus status, String detail) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("status", status.value());
    body.put("title", status.getReasonPhrase());
    body.put("detail", detail);
    return body;
  }

  static void write(HttpServletResponse response, HttpStatus status, String detail)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    try {
      response.getWriter().write(MAPPER.writeValueAsString(body(status, detail)));
    } catch (JsonProcessingException e) {
      response.getWriter().write("{\"status\":" + status.value() + "}");
    }
  }
}
