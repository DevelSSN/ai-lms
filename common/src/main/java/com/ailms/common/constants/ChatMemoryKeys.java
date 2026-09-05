package com.ailms.common.constants;

public final class ChatMemoryKeys {

  public static final String CONVERSATION_PREFIX = "conversation:";
  public static final String PROFILING_PREFIX = "profiling:";

  private ChatMemoryKeys() {}

  public static String conversation(String sessionId) {
    return CONVERSATION_PREFIX + sessionId;
  }

  public static String profiling(String sessionId) {
    return PROFILING_PREFIX + sessionId;
  }
}