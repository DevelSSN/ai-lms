package com.ailms.common.constants;

public final class VectorSourceKeys {

  public static final String DOCUMENT_PREFIX = "doc:";

  private VectorSourceKeys() {}

  public static String document(String documentId) {
    return DOCUMENT_PREFIX + documentId;
  }
}