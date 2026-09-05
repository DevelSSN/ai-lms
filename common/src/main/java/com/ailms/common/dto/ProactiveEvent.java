package com.ailms.common.dto;

/** Kafka payload for a generated proactive follow-up to be pushed to the owning user. */
public record ProactiveEvent(String userId, String context, String eventType) {}
