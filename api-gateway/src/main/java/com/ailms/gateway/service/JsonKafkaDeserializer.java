package com.ailms.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Deserializer;

/** Jackson-based Kafka deserializer for the gateway (avoids a JSON-B runtime dependency). */
public abstract class JsonKafkaDeserializer<T> implements Deserializer<T> {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final Class<T> targetType;

  protected JsonKafkaDeserializer(Class<T> targetType) {
    this.targetType = targetType;
  }

  @Override
  public T deserialize(String topic, byte[] data) {
    if (data == null) return null;
    try {
      return OBJECT_MAPPER.readValue(data, targetType);
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Failed to deserialize Kafka message on topic " + topic, e);
    }
  }
}
