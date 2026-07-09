package com.engperf.application.port.outbound;

/** Outbound port: supplies the next echo sequence number (implemented by an outbound adapter). */
public interface EchoCounterPort {

  long nextSequence();
}
