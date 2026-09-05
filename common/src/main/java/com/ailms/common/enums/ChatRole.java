package com.ailms.common.enums;

public enum ChatRole {
  USER("user"),
  ASSISTANT("assistant");

  private final String key;

  ChatRole(String key) {
    this.key = key;
  }

  public String key() {
    return key;
  }

  public static boolean isUser(String role) {
    return USER.key.equals(role);
  }

  public static boolean isAssistant(String role) {
    return ASSISTANT.key.equals(role);
  }
}