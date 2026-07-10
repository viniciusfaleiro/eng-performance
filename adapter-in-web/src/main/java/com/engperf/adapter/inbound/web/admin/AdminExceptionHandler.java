package com.engperf.adapter.inbound.web.admin;

import com.engperf.domain.common.ConflictException;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Translates domain/application exceptions to HTTP problem responses for the admin endpoints. */
@RestControllerAdvice(basePackages = "com.engperf.adapter.inbound.web.admin")
public class AdminExceptionHandler {

  @ExceptionHandler(NoSuchElementException.class)
  public ResponseEntity<Map<String, Object>> onNotFound(NoSuchElementException ex) {
    return problem(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<Map<String, Object>> onConflict(ConflictException ex) {
    return problem(HttpStatus.CONFLICT, ex.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> onValidation(IllegalArgumentException ex) {
    return problem(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
  }

  private static ResponseEntity<Map<String, Object>> problem(HttpStatus status, String detail) {
    return ResponseEntity.status(status)
        .body(
            Map.of("status", status.value(), "title", status.getReasonPhrase(), "detail", detail));
  }
}
