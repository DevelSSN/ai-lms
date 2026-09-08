package com.ailms.orchestrator.eval;

import com.ailms.orchestrator.util.TextUtils;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Replicates the deterministic routing prefix of {@code OrchestratorService.route} for offline
 * scoring: bare-greeting short-circuit, explicit-video-link short-circuit, else classifier. Test
 * scope only. Does not replicate {@code reclassifyContentIntent} (needs session/document state).
 */
final class TestRouter {

  // Must stay in sync with OrchestratorService.EXPLICIT_VIDEO_LINK.
  static final Pattern EXPLICIT_VIDEO_LINK =
      Pattern.compile("(?i)\\b(?:https?://|www\\.)?(?:m\\.)?(?:youtube\\.com|youtu\\.be)/");

  static final Set<String> KNOWN_INTENTS =
      Set.of("CONVERSATION", "VIDEO_SEARCH", "CONTENT_ANALYSIS", "ASSESSMENT", "INSIGHT");

  private TestRouter() {}

  static String route(String message, Function<String, String> classifier) {
    if (TextUtils.isBareGreeting(message)) {
      return "CONVERSATION";
    }
    if (message != null && EXPLICIT_VIDEO_LINK.matcher(message).find()) {
      return "VIDEO_SEARCH";
    }
    return normalizeIntent(classifier.apply(message));
  }

  /** Mirrors OrchestratorService.normalizeIntent: unknown/blank output defaults to CONVERSATION. */
  static String normalizeIntent(String raw) {
    if (raw == null) {
      return "CONVERSATION";
    }
    String normalized = raw.trim().toUpperCase(Locale.ROOT).replaceFirst("\\.$", "");
    if (!KNOWN_INTENTS.contains(normalized)) {
      return "CONVERSATION";
    }
    return normalized;
  }
}
