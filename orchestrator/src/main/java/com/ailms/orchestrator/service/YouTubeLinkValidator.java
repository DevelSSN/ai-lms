package com.ailms.orchestrator.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Slf4j
@ApplicationScoped
public class YouTubeLinkValidator {

  private static final String URL_FORM =
      "(?:https?://(?:www\\.)?(?:youtube\\.com/watch\\?v=|youtu\\.be/)[A-Za-z0-9_-]+)";

  private static final Pattern YOUTUBE_URL_PATTERN = Pattern.compile(URL_FORM);

  private static final Pattern MARKDOWN_LINK_PATTERN =
      Pattern.compile("\\[([^\\]]*)\\]\\(" + "(" + URL_FORM + ")\\)");

  private static final Pattern ID_EXTRACTOR =
      Pattern.compile("(?:youtube\\.com/watch\\?v=|youtu\\.be/)([A-Za-z0-9_-]+)");

  private static final int YOUTUBE_ID_LENGTH = 11;

  @ConfigProperty(name = "ailms.youtube.oembed-timeout", defaultValue = "3s")
  Duration timeout;

  private final HttpClient httpClient = HttpClient.newHttpClient();

  public String sanitize(String text) {
    if (text == null || text.isBlank()) return text;

    text = sanitizeMarkdownLinks(text);
    text = sanitizeBareUrls(text);
    return text;
  }

  private String sanitizeMarkdownLinks(String text) {
    Matcher matcher = MARKDOWN_LINK_PATTERN.matcher(text);
    if (!matcher.find()) return text;
    matcher.reset();

    StringBuffer sb = new StringBuffer();
    while (matcher.find()) {
      String match = matcher.group();
      String label = matcher.group(1);
      String replacement = isValidYoutubeUrl(matcher.group(2)) ? match : label;
      matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(sb);
    return sb.toString();
  }

  private String sanitizeBareUrls(String text) {
    Matcher matcher = YOUTUBE_URL_PATTERN.matcher(text);
    if (!matcher.find()) return text;
    matcher.reset();

    StringBuffer sb = new StringBuffer();
    while (matcher.find()) {
      String match = matcher.group();
      if (isValidYoutubeUrl(match)) {
        matcher.appendReplacement(sb, Matcher.quoteReplacement(match));
      } else {
        matcher.appendReplacement(sb, "");
      }
    }
    matcher.appendTail(sb);
    return sb.toString();
  }

  private boolean isValidYoutubeUrl(String url) {
    Matcher idMatcher = ID_EXTRACTOR.matcher(url);
    if (!idMatcher.find()) return false;
    String videoId = idMatcher.group(1);
    if (videoId.length() != YOUTUBE_ID_LENGTH) return false;
    return isValidVideoId(videoId);
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
      log.warn(
          "YouTube oEmbed validation failed for videoId={}, failing closed: {}",
          videoId,
          e.getMessage());
      return false;
    }
  }
}
