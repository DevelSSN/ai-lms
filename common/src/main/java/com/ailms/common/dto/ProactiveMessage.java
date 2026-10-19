package com.ailms.common.dto;

import java.time.Instant;

/** A persisted proactive (follow-up) message, used by the polling fallback. */
public record ProactiveMessage(String id, String response, String eventType, Instant timestamp) {}
