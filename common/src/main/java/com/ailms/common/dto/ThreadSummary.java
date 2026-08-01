package com.ailms.common.dto;

import java.time.Instant;

public record ThreadSummary(
    String sessionId, String title, Instant lastActive, long messageCount) {}
