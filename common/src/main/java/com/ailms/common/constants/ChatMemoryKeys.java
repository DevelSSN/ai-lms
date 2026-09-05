package com.ailms.common.constants;

public final class ChatMemoryKeys {

  public static final String CONVERSATION_PREFIX = "conversation:";
  public static final String ANALYSIS_PREFIX = "analysis:";
  public static final String ASSESSMENT_PREFIX = "assessment:";
  public static final String INSIGHT_PREFIX = "insight:";
  public static final String PROFILING_PREFIX = "profiling:";

  private ChatMemoryKeys() {}

  public static String conversation(String sessionId) {
    return CONVERSATION_PREFIX + sessionId;
  }

  public static String analysis(String sessionId) {
    return ANALYSIS_PREFIX + sessionId;
  }

  public static String assessment(String sessionId) {
    return ASSESSMENT_PREFIX + sessionId;
  }

  public static String insight(String sessionId) {
    return INSIGHT_PREFIX + sessionId;
  }

  public static String profiling(String sessionId) {
    return PROFILING_PREFIX + sessionId;
  }
}