package com.ailms.orchestrator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@ApplicationScoped
public class YouTubeSearchService {

  public record VideoResult(String title, String videoId) {}

  private static final String SEARCH_ENDPOINT =
      "https://www.googleapis.com/youtube/v3/search";

  private static final int MAX_RESULTS = 3;

  private static final List<Pattern> PREAMBLE_PATTERNS =
      List.of(
          Pattern.compile(
              "(?i)^\\s*(?:please\\s+)?(?:give\\s+me\\s+(?:the\\s+)?|find\\s+me\\s+|"
                  + "recommend\\s+(?:me\\s+)?|show\\s+me\\s+|i\\s+want\\s+)"
                  + "(?:a\\s+)?youtube\\s+(?:link|video)\\s+(?:to|about|on|for)\\s+"),
          Pattern.compile(
              "(?i)^\\s*(?:can\\s+you\\s+(?:please\\s+)?)?"
                  + "(?:find\\s+me\\s+|recommend\\s+(?:me\\s+)?|show\\s+me\\s+)"
                  + "(?:a\\s+)?(?:good\\s+)?video\\s+(?:about|on)\\s+"),
          Pattern.compile("(?i)^\\s*(?:a\\s+)?(?:good\\s+)?video\\s+(?:about|on)\\s+"));

  @ConfigProperty(name = "youtube.api.key", defaultValue = "")
  String apiKey;

  @ConfigProperty(name = "ailms.youtube.search-timeout", defaultValue = "5s")
  Duration timeout;

  private final HttpClient httpClient = HttpClient.newHttpClient();

  private final ObjectMapper objectMapper = new ObjectMapper();

  public List<VideoResult> search(String query) {
    if (query == null || query.isBlank() || apiKey == null || apiKey.isBlank()) {
      return List.of();
    }
    try {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(buildUrl(query))).timeout(timeout).GET().build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        log.warn("YouTube search API returned status={} for query={}", response.statusCode(), query);
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
    String query = trimmed;
    for (Pattern pattern : PREAMBLE_PATTERNS) {
      Matcher matcher = pattern.matcher(trimmed);
      if (matcher.find()) {
        String rest = trimmed.substring(matcher.end()).trim();
        if (!rest.isEmpty()) {
          query = rest;
          break;
        }
      }
    }
    return query.replaceFirst("[?.!,;]+$", "").trim();
  }
}
