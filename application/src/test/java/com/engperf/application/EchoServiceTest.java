package com.engperf.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.engperf.application.port.outbound.EchoCounterPort;
import com.engperf.domain.Message;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class EchoServiceTest {

  private final AtomicLong sequence = new AtomicLong();
  private final EchoCounterPort counter = sequence::incrementAndGet;
  private final EchoService service = new EchoService(counter);

  @Test
  void echoesTextBack() {
    EchoResult result = service.echo(Message.of("hello"));
    assertThat(result.text()).isEqualTo("hello");
  }

  @Test
  void assignsIncreasingSequence() {
    long first = service.echo(Message.of("a")).sequence();
    long second = service.echo(Message.of("b")).sequence();
    assertThat(first).isEqualTo(1L);
    assertThat(second).isEqualTo(2L);
  }
}
