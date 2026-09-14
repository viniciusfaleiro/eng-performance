package com.engperf.adapter.inbound.web.auth;

import com.engperf.application.auth.AuthenticationException;
import com.engperf.application.auth.InvalidResetTokenException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps authentication (401), authorization (403) and invalid-reset-token (400) failures raised
 * inside controllers.
 */
@RestControllerAdvice
public class AuthWebExceptionHandler {

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<Map<String, Object>> onUnauthorized(AuthenticationException ex) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(Problem.body(HttpStatus.UNAUTHORIZED, ex.getMessage()));
  }

  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<Map<String, Object>> onForbidden(ForbiddenException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(Problem.body(HttpStatus.FORBIDDEN, ex.getMessage()));
  }

  @ExceptionHandler(InvalidResetTokenException.class)
  public ResponseEntity<Map<String, Object>> onInvalidResetToken(InvalidResetTokenException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(Problem.body(HttpStatus.BAD_REQUEST, ex.getMessage()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> onBadRequest(IllegalArgumentException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(Problem.body(HttpStatus.BAD_REQUEST, ex.getMessage()));
  }
}
