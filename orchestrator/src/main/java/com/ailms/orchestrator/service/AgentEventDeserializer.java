package com.ailms.orchestrator.service;

import com.ailms.common.dto.AgentEvent;
import io.quarkus.kafka.client.serialization.JsonbDeserializer;

public class AgentEventDeserializer extends JsonbDeserializer<AgentEvent> {

  public AgentEventDeserializer() {
    super(AgentEvent.class);
  }
}
