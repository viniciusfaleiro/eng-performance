package com.engperf.application;

import com.engperf.application.port.inbound.EchoUseCase;
import com.engperf.application.port.outbound.EchoCounterPort;
import com.engperf.domain.Message;
import java.util.Objects;

/** Application service implementing {@link EchoUseCase}. Plain Java — wired by the bootstrap. */
public final class EchoService implements EchoUseCase {

  private final EchoCounterPort counter;

  public EchoService(EchoCounterPort counter) {
    this.counter = Objects.requireNonNull(counter, "counter must not be null");
  }

  @Override
  public EchoResult echo(Message message) {
    Objects.requireNonNull(message, "message must not be null");
    long sequence = counter.nextSequence();
    return new EchoResult(message.text(), sequence);
  }
}
