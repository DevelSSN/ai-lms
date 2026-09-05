package com.ailms.orchestrator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Slf4j
@ApplicationScoped
public class YouTubeSearchService {

  public record VideoResult(String title, String videoId) {}

  private static final String SEARCH_ENDPOINT = "https://www.googleapis.com/youtube/v3/search";

  private static final int MAX_RESULTS = 3;

  private static final List<Pattern> PREAMBLE_PATTERNS =
      List.of(
          Pattern.compile("(?i)give\\s+(?:me\\s+)?(?:the\\s+)?youtube\\s+(?:videos|video|link)"),
          Pattern.compile("(?i)i\\s+want\\s+(?:a\\s+|the\\s+)?youtube\\s+(?:videos|video|link)"),
          Pattern.compile(
              "(?i)(?:find|show|recommend)\\s+(?:me\\s+)?(?:a\\s+)?(?:good\\s+)?youtube\\s+(?:videos|video|link)"),
          Pattern.compile("(?i)youtube\\s+(?:videos|video|link)"),
          Pattern.compile(
              "(?i)(?:recommend|find|show)\\s+(?:me\\s+)?(?:a\\s+)?(?:good\\s+)?video(?:s)?\\s+(?:about|on)"),
          Pattern.compile("(?i)(?:a\\s+|good\\s+)?video(?:s)?\\s+(?:about|on|for)"),
          Pattern.compile(
              "(?i)(?:recommend|find|show|share|send)\\s+(?:me\\s+)?(?:a\\s+|some\\s+)?videos?"),
          Pattern.compile("(?i)give\\s+(?:me\\s+)?(?:some\\s+|the\\s+)?videos?"),
          Pattern.compile("(?i)i\\s+want\\s+(?:a\\s+|some\\s+|the\\s+)?videos?"));

  private static final List<String> TOPIC_PHRASE_PREFIXES =
      List.of(
          "master plan for",
          "master plan of",
          "study plan for",
          "lesson plan for",
          "roadmap for",
          "crash course on",
          "tutorial on",
          "introduction to",
          "guide to",
          "tell me about",
          "teach me",
          "explain",
          "learn about",
          "learn",
          "what is",
          "what are",
          "what's",
          "about");

  private static final Set<String> COMMAND_WORDS =
      Set.of(
          "give", "me", "show", "find", "recommend", "send", "share", "want", "i", "need",
          "some", "the", "a", "an", "please", "ok", "okay", "yes", "no", "youtube",
          "video", "videos", "link", "links", "watch", "for", "on", "about", "to", "of");

  @ConfigProperty(name = "youtube.api.key", defaultValue = "")
  String apiKey;

  @ConfigProperty(name = "ailms.youtube.search-timeout", defaultValue = "5s")
  Duration timeout;

  private final HttpClient httpClient = HttpClient.newHttpClient();

  private final ObjectMapper objectMapper = new ObjectMapper();

  public List<VideoResult> search(String query) {
    if (query == null || query.isBlank()) {
      return List.of();
    }
    if (apiKey == null || apiKey.isBlank()) {
      log.warn("YouTube API key missing — search disabled");
      return List.of();
    }
    try {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(buildUrl(query))).timeout(timeout).GET().build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        log.warn(
            "YouTube search API returned status={} for query={}", response.statusCode(), query);
        return List.of();
      }
      return parse(response.body());
    } catch (Exception e) {
      log.warn("YouTube search failed for query={}: {}", query, e.getMessage());
      return List.of();
    }
  }

  String buildUrl(String query) {
    return SEARCH_ENDPOINT
        + "?part=snippet&type=video&maxResults="
        + MAX_RESULTS
        + "&q="
        + URLEncoder.encode(query, StandardCharsets.UTF_8)
        + "&key="
        + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
  }

  List<VideoResult> parse(String json) {
    List<VideoResult> results = new ArrayList<>();
    try {
      JsonNode items = objectMapper.readTree(json).path("items");
      for (JsonNode item : items) {
        String videoId = item.path("id").path("videoId").asText(null);
        if (videoId == null || videoId.isBlank()) continue;
        String title = item.path("snippet").path("title").asText("Untitled video");
        results.add(new VideoResult(title, videoId));
      }
    } catch (Exception e) {
      log.warn("Failed to parse YouTube search response: {}", e.getMessage());
      return List.of();
    }
    return results;
  }

  String extractQuery(String message) {
    if (message == null) return null;
    String trimmed = message.trim();
    String rest = trimmed;
    for (Pattern pattern : PREAMBLE_PATTERNS) {
      Matcher matcher = pattern.matcher(trimmed);
      if (matcher.find()) {
        rest = trimmed.substring(matcher.end());
        break;
      }
    }
    String topic = cleanTopic(rest);
    return (topic == null || topic.isBlank()) ? "" : topic;
  }

  private String cleanTopic(String raw) {
    if (raw == null) return null;
    String text = raw.trim();
    text = text.replaceFirst("[?.!,;\\s]+$", "").trim();
    text = text.replaceFirst("(?i)^\\s*(?:about|on|for|to)\\s*", "").trim();

    boolean changed = true;
    while (changed) {
      changed = false;
      for (String phrase : TOPIC_PHRASE_PREFIXES) {
        String candidate = stripPhrasePrefix(text, phrase);
        if (candidate != null) {
          text = candidate;
          changed = true;
          break;
        }
      }
    }

    text = text.replaceFirst("(?i)^(?:a|an|the)\\s+", "").trim();

    if (text.isBlank()) return null;

    String normalized = text.toLowerCase(Locale.ROOT);
    String[] tokens = normalized.split("[^a-z0-9]+");
    for (String token : tokens) {
      if (token.isEmpty()) continue;
      if (!COMMAND_WORDS.contains(token)) return text;
    }
    return null;
  }

  private String stripPhrasePrefix(String text, String phrase) {
    String lower = text.toLowerCase(Locale.ROOT);
    if (!lower.startsWith(phrase)) return null;
    int len = phrase.length();
    if (len < lower.length() && !Character.isWhitespace(lower.charAt(len))) return null;
    return text.substring(len).trim();
  }
}
