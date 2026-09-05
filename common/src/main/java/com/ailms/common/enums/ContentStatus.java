package com.ailms.common.enums;

public enum ContentStatus {
  UPLOADED,
  PARSED,
  INDEXED,
  FAILED;

  public static ContentStatus fromName(String name) {
    if (name == null) return UPLOADED;
    for (ContentStatus status : values()) {
      if (status.name().equalsIgnoreCase(name)) return status;
    }
    return UPLOADED;
  }
}