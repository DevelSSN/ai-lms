package com.ailms.gateway.service;

import com.ailms.common.dto.ProactiveEvent;

public class ProactiveEventDeserializer extends JsonKafkaDeserializer<ProactiveEvent> {

  public ProactiveEventDeserializer() {
    super(ProactiveEvent.class);
  }
}
