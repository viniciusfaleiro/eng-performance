package com.engperf.application.port.inbound;

import com.engperf.application.EchoResult;
import com.engperf.domain.Message;

/** Inbound port: echo a message back, tagged with a monotonically increasing sequence number. */
public interface EchoUseCase {

  EchoResult echo(Message message);
}
