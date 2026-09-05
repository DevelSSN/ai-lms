package com.ailms.common.enums;

public enum IntentType {
  CONVERSATION,
  VIDEO_SEARCH,
  CONTENT_ANALYSIS,
  ASSESSMENT,
  INSIGHT;

  public static IntentType fromName(String raw) {
    if (raw == null) return CONVERSATION;
    return java.util.Arrays.stream(values())
        .filter(v -> v.name().equals(raw.trim().toUpperCase(java.util.Locale.ROOT)))
        .findFirst()
        .orElse(CONVERSATION);
  }

  public static boolean isAnalysis(String intent) {
    return CONTENT_ANALYSIS.name().equals(intent) || ASSESSMENT.name().equals(intent);
  }
}
