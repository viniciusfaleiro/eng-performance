package com.engperf.adapter.inbound.web;

import com.engperf.application.EchoResult;
import com.engperf.application.port.inbound.EchoUseCase;
import com.engperf.domain.Message;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Inbound REST adapter exposing the echo use case. */
@RestController
public class EchoController {

  private final EchoUseCase echoUseCase;

  public EchoController(EchoUseCase echoUseCase) {
    this.echoUseCase = echoUseCase;
  }

  @GetMapping("/api/echo")
  public EchoResult echo(@RequestParam(name = "message", defaultValue = "") String message) {
    return echoUseCase.echo(Message.of(message));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, String> onInvalidMessage(IllegalArgumentException ex) {
    return Map.of("error", ex.getMessage());
  }
}
