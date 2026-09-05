package com.ailms.orchestrator.service;

import com.ailms.common.dto.ProactiveEvent;
import io.quarkus.kafka.client.serialization.JsonbDeserializer;

public class ProactiveEventDeserializer extends JsonbDeserializer<ProactiveEvent> {

  public ProactiveEventDeserializer() {
    super(ProactiveEvent.class);
  }
}
