package com.ailms.orchestrator.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class YouTubeSearchServiceTest {

  YouTubeSearchService service;

  @BeforeEach
  void setUp() {
    service = new YouTubeSearchService();
    service.apiKey = "test-key";
  }

  @Test
  void search_blankKeyReturnsEmpty() {
    service.apiKey = "";
    assertTrue(service.search("neural networks").isEmpty());
  }

  @Test
  void search_blankQueryReturnsEmpty() {
    assertTrue(service.search("   ").isEmpty());
    assertTrue(service.search(null).isEmpty());
  }

  @Test
  void buildUrl_encodesQueryAndKey() {
    String url = service.buildUrl("neural networks 3blue1brown");
    assertTrue(
        url.startsWith(
            "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=3"));
    assertTrue(url.contains("q=neural+networks+3blue1brown"));
    assertTrue(url.contains("key=test-key"));
  }

  @Test
  void parse_extractsVideoResults() {
    String json =
        """
        {
          "items": [
            {
              "id": { "kind": "youtube#video", "videoId": "aircAruvnKk" },
              "snippet": { "title": "But what is a neural network?" }
            },
            {
              "id": { "kind": "youtube#video", "videoId": "IHZwWFHWa-w" },
              "snippet": { "title": "Gradient descent" }
            }
          ]
        }
        """;

    List<YouTubeSearchService.VideoResult> results = service.parse(json);

    assertEquals(2, results.size());
    assertEquals("But what is a neural network?", results.get(0).title());
    assertEquals("aircAruvnKk", results.get(0).videoId());
    assertEquals("IHZwWFHWa-w", results.get(1).videoId());
  }

  @Test
  void parse_emptyItemsReturnsEmpty() {
    assertTrue(service.parse("{\"items\": []}").isEmpty());
  }

  @Test
  void parse_skipsItemsWithoutVideoId() {
    String json =
        """
        {
          "items": [
            { "id": { "kind": "youtube#channel", "channelId": "UCEb9v7gq" }, "snippet": { "title": "channel" } },
            { "id": { "kind": "youtube#video", "videoId": "aircAruvnKk" }, "snippet": { "title": "video" } }
          ]
        }
        """;

    List<YouTubeSearchService.VideoResult> results = service.parse(json);

    assertEquals(1, results.size());
    assertEquals("aircAruvnKk", results.get(0).videoId());
  }

  @Test
  void parse_malformedJsonReturnsEmpty() {
    assertTrue(service.parse("not json").isEmpty());
  }

  @Test
  void extractQuery_stripsPreamble() {
    assertEquals(
        "Neural networks by 3b1b",
        service.extractQuery("Give me a youtube link to Neural networks by 3b1b"));
    assertEquals(
        "Neural networks by 3b1b",
        service.extractQuery("give me the youtube link for Neural networks by 3b1b"));
    assertEquals("calculus", service.extractQuery("Recommend a good video about calculus"));
    assertEquals(
        "quantum mechanics", service.extractQuery("Can you find me a video on quantum mechanics?"));
    assertEquals("universe", service.extractQuery("Show me a video about the universe"));
    assertEquals("neural network", service.extractQuery("What is a neural network?"));
  }

  @Test
  void extractQuery_handlesBareAndMultilineVideoRequests() {
    assertEquals("", service.extractQuery("Give youtube videos"));
    assertEquals("", service.extractQuery("Ok\nGive youtube videos"));
  }

  @Test
  void extractQuery_extractsTopicFromVideoRequests() {
    assertEquals("git", service.extractQuery("youtube videos about git"));
    assertEquals("git", service.extractQuery("Ok give youtube videos about git"));
    assertEquals("git", service.extractQuery("give youtube videos for git"));
    assertEquals("calculus", service.extractQuery("find me videos on calculus"));
  }

  @Test
  void extractQuery_returnsBlankForBareRequests() {
    assertEquals("", service.extractQuery("Give me videos"));
    assertEquals("", service.extractQuery("give videos"));
    assertEquals("", service.extractQuery("Give me youtube videos"));
    assertEquals("", service.extractQuery("Show me some videos"));
    assertEquals("", service.extractQuery("videos please"));
  }

  @Test
  void extractQuery_extractsTopicFromConversationContextMessages() {
    assertEquals("git", service.extractQuery("Master plan for git"));
    assertEquals("git", service.extractQuery("Tutorial on git"));
    assertEquals("docker", service.extractQuery("Guide to docker"));
    assertEquals("calculus", service.extractQuery("Teach me calculus"));
    assertEquals("machine learning", service.extractQuery("Show me videos about machine learning"));
    assertEquals("linux", service.extractQuery("i want videos on linux"));
    assertEquals("shell scripting", service.extractQuery("tell me about shell scripting"));
  }
}
