package com.ailms.orchestrator.service;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@ApplicationScoped
public class YouTubeLinkValidator {

  private static final Pattern YOUTUBE_URL_PATTERN =
      Pattern.compile(
          "https?://(?:www\\.)?(?:youtube\\.com/watch\\?v=|youtu\\.be/)([A-Za-z0-9_-]{11})(?=\\s|&|$)");

  @ConfigProperty(name = "ailms.youtube.oembed-timeout", defaultValue = "3s")
  Duration timeout;

  private final HttpClient httpClient = HttpClient.newHttpClient();

  public String sanitize(String text) {
    if (text == null || text.isBlank()) return text;

    Matcher matcher = YOUTUBE_URL_PATTERN.matcher(text);
    if (!matcher.find()) return text;
    matcher.reset();

    StringBuffer sb = new StringBuffer();
    boolean changed = false;
    while (matcher.find()) {
      String videoId = matcher.group(1);
      if (isValidVideoId(videoId)) {
        matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
      } else {
        matcher.appendReplacement(sb, "");
        changed = true;
      }
    }
    matcher.appendTail(sb);
    return changed ? sb.toString() : text;
  }

  boolean isValidVideoId(String videoId) {
    try {
      String oembedUrl =
          "https://www.youtube.com/oembed?url="
              + URLEncoder.encode(
                  "https://www.youtube.com/watch?v=" + videoId, StandardCharsets.UTF_8)
              + "&format=json";
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(oembedUrl)).timeout(timeout).GET().build();
      HttpResponse<Void> response =
          httpClient.send(request, HttpResponse.BodyHandlers.discarding());
      return response.statusCode() == 200;
    } catch (Exception e) {
      log.warn("YouTube oEmbed validation failed for videoId={}: {}", videoId, e.getMessage());
      return true;
    }
  }
}
