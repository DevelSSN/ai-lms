package com.ailms.orchestrator.service;

import io.quarkus.kafka.client.serialization.JsonbDeserializer;

public class AgentEventDeserializer extends JsonbDeserializer<KafkaEventPublisher.AgentEvent> {

  public AgentEventDeserializer() {
    super(KafkaEventPublisher.AgentEvent.class);
  }
}
