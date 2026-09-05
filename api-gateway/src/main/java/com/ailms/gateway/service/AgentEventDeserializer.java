package com.ailms.gateway.service;

import com.ailms.common.dto.AgentEvent;

public class AgentEventDeserializer extends JsonKafkaDeserializer<AgentEvent> {

  public AgentEventDeserializer() {
    super(AgentEvent.class);
  }
}