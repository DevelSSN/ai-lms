package com.ailms.common.dto;

/** Kafka payload describing an agent pipeline event (content analysis, profile, insight). */
public record AgentEvent(String userId, String sessionId, String data, String eventType) {}
