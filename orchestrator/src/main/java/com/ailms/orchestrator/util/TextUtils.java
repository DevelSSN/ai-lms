package com.ailms.orchestrator.util;

import java.util.regex.Pattern;

public final class TextUtils {

  private static final int MAX_GREETING_LENGTH = 30;

  private static final Pattern BARE_GREETING =
      Pattern.compile(
          "^(?:"
              + "(?:hi|hiya|hello|heya|hey|yo|sup|namaste|namaskar|hola)(?:\\s+there)?"
              + "|good\\s+(?:morning|afternoon|evening)"
              + "|how(?:'s| is| are)?\\s+(?:it\\s+going|things\\s+going|you\\s+doing|are\\s+you|are\\s+things)"
              + ")[\\s!.,'?]*$",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern THINK_BLOCK =
      Pattern.compile(
          "(?is)\\bresponse\\s*<think\\b.*?</think\\s*>|<think\\b.*?</think\\s*>");

  private static final Pattern THINK_REMNANT = Pattern.compile("(?is)</?think\\s*>");

  private TextUtils() {}

  public static boolean isBareGreeting(String message) {
    if (message == null) return false;
    String trimmed = message.trim();
    if (trimmed.isEmpty() || trimmed.length() > MAX_GREETING_LENGTH) return false;
    return BARE_GREETING.matcher(trimmed).matches();
  }

  public static String stripThinking(String text) {
    if (text == null) return null;
    String stripped = THINK_BLOCK.matcher(text).replaceAll(" ");
    stripped = THINK_REMNANT.matcher(stripped).replaceAll(" ");
    return stripped.replaceAll("\\s+", " ").trim();
  }
}