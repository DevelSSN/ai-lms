package com.ailms.common.util;

import java.util.Locale;
import java.util.Set;

/** Central policy for which uploaded content types the pipeline can extract text from. */
public final class ContentTypeSupport {

  private static final Set<String> PARSABLE_TYPES =
      Set.of(
          "application/pdf",
          "application/msword",
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
          "application/rtf",
          "application/vnd.oasis.opendocument.text",
          "image/png",
          "image/jpeg",
          "image/tiff");

  private ContentTypeSupport() {}

  /** True for types that can be decoded directly as UTF-8 text (txt, md, csv, html, json, xml). */
  public static boolean isTextual(String contentType) {
    if (contentType == null) return false;
    String ct = normalize(contentType);
    return ct.startsWith("text/")
        || ct.equals("application/json")
        || ct.equals("application/xml")
        || ct.endsWith("+json")
        || ct.endsWith("+xml");
  }

  /** True for binary types handled by the document parser (PDF, Office, OCR-able images). */
  public static boolean isParsable(String contentType) {
    return contentType != null && PARSABLE_TYPES.contains(normalize(contentType));
  }

  public static boolean isSupported(String contentType) {
    return isTextual(contentType) || isParsable(contentType);
  }

  private static String normalize(String contentType) {
    return contentType.toLowerCase(Locale.ROOT).trim();
  }
}
