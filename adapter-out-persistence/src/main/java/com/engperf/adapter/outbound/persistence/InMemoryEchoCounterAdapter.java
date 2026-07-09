package com.engperf.adapter.outbound.persistence;

import com.engperf.application.port.outbound.EchoCounterPort;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Outbound adapter backing {@link EchoCounterPort} with an in-process counter.
 *
 * <p>Placeholder for a real datastore: swapping this for a JPA/Redis adapter requires no change to
 * the domain or application layers.
 */
@Component
public class InMemoryEchoCounterAdapter implements EchoCounterPort {

  private final AtomicLong sequence = new AtomicLong();

  @Override
  public long nextSequence() {
    return sequence.incrementAndGet();
  }
}
