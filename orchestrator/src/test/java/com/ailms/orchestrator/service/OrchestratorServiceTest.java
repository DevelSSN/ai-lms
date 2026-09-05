package com.ailms.orchestrator.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ailms.common.constants.ChatMemoryKeys;
import com.ailms.common.dto.ChatHistory;
import com.ailms.common.dto.ChatRequest;
import com.ailms.common.dto.ChatResponse;
import com.ailms.orchestrator.agent.ContentAnalysisAgent;
import com.ailms.orchestrator.agent.ConversationAgent;
import com.ailms.orchestrator.agent.InsightAgent;
import com.ailms.orchestrator.agent.IntentClassifier;
import com.ailms.orchestrator.agent.ProfilingAgent;
import com.ailms.orchestrator.agent.QuestionGenerationAgent;
import com.ailms.orchestrator.agent.ResponseComposer;
import com.ailms.orchestrator.agent.ResponseVerifierAgent;
import com.ailms.orchestrator.repository.ConversationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.data.message.UserMessage;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrchestratorServiceTest {

  @Mock IntentClassifier intentClassifier;
  @Mock ProfilingAgent profilingAgent;
  @Mock ResponseComposer responseComposer;
  @Mock ConversationAgent conversationAgent;
  @Mock ContentAnalysisAgent contentAnalysisAgent;
  @Mock QuestionGenerationAgent questionGenerationAgent;
  @Mock ResponseVerifierAgent responseVerifierAgent;
  @Mock InsightAgent insightAgent;
  @Mock ProfilingService profilingService;
  @Mock ConversationRepository conversationRepository;
  @Mock VectorDBService vectorDBService;
  @Mock ContentDocumentService contentDocumentService;
  @Mock InsightDataService insightDataService;
  @Mock KafkaEventPublisher kafkaEventPublisher;
  @Mock YouTubeLinkValidator youTubeLinkValidator;
  @Mock YouTubeSearchService youTubeSearchService;
  @Mock RedisChatMemoryStore chatMemoryStore;

  @BeforeEach
  void stubVerifierDefaultAccept() {
    lenient()
        .when(responseVerifierAgent.verify(anyString(), anyString(), anyString()))
        .thenReturn("{\"verdict\": \"ACCEPT\", \"reason\": \"ok\"}");
  }

  @Test
  void route_rejectsSessionOwnedByAnotherUser() {
    when(conversationRepository.sessionOwner("sess-1")).thenReturn("user-other");

    OrchestratorService svc = buildService();

    assertThrows(
        SessionOwnershipException.class,
        () -> svc.route(new ChatRequest("hello", "sess-1"), "user-1"));
    verify(conversationAgent, never()).process(anyString(), anyString());
  }

  @Test
  void route_allowsSessionOwnedBySameUser() {
    when(conversationRepository.sessionOwner("sess-1")).thenReturn("user-1");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenAnswer(
            inv -> {
              AgenticScope scope = inv.getArgument(0);
              return new ChatResponse(
                  scope.readState("response", "unset"), "sess-1", "CONVERSATION");
            });

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest("hello", "sess-1"), "user-1");

    assertEquals("Hello! I'm your AI tutor. What would you like to learn today?", resp.message());
  }

  @Test
  void greetsWithStateStoredUnderResponseKey() {
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenAnswer(
            inv -> {
              AgenticScope scope = inv.getArgument(0);
              return new ChatResponse(
                  scope.readState("response", "unset"), "sess-1", "CONVERSATION");
            });
    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest("hello", "sess-1"), "user-1");
    assertEquals("Hello! I'm your AI tutor. What would you like to learn today?", resp.message());
  }

  @Test
  void route_bareGreeting_shortCircuits() {
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenAnswer(
            inv -> {
              AgenticScope scope = inv.getArgument(0);
              String msg = scope.readState("response", "");
              return new ChatResponse(msg, "sess-1", "CONVERSATION");
            });

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest("hello", "sess-1"), "user-1");

    assertEquals("Hello! I'm your AI tutor. What would you like to learn today?", resp.message());
    assertEquals("CONVERSATION", resp.agentType());
    verify(profilingService).ensureProfile("user-1");
    verify(intentClassifier, never()).classify(anyString());
    verify(conversationAgent, never()).process(anyString(), anyString());
    verify(profilingAgent, never()).process(anyString(), anyString());
    verify(conversationRepository).logMessage(anyString(), anyString(), eq("user"), anyString());
  }

  @Test
  void route_generatesSessionWhenMissing() {
    when(responseComposer.compose(any(AgenticScope.class), anyString()))
        .thenAnswer(
            inv -> {
              String sid = inv.getArgument(1);
              AgenticScope scope = inv.getArgument(0);
              String msg = scope.readState("response", "");
              return new ChatResponse(msg, sid, "CONVERSATION");
            });

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest("hi", null), "user-1");

    assertNotNull(resp.sessionId());
    assertFalse(resp.sessionId().isBlank());
    assertEquals("Hello! I'm your AI tutor. What would you like to learn today?", resp.message());
    verify(conversationRepository).logMessage(eq("user-1"), anyString(), eq("user"), anyString());
  }

  @Test
  void route_normalizesClassifierOutput() {
    when(intentClassifier.classify("what is a neural network")).thenReturn("\nconversation. ");
    when(conversationAgent.process(eq("conversation:sess-1"), anyString()))
        .thenReturn("Hello there!");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenReturn(new ChatResponse("Hello there!", "sess-1", "CONVERSATION"));

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest("what is a neural network", "sess-1"), "user-1");

    assertEquals("Hello there!", resp.message());
    verify(conversationAgent).process(eq("conversation:sess-1"), anyString());
  }

  @Test
  void route_defaultsUnrecognizedIntentToConversation() {
    when(intentClassifier.classify("huh")).thenReturn("SOMETHING_ELSE");
    when(conversationAgent.process(eq("conversation:sess-1"), anyString()))
        .thenReturn("Hello there!");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenReturn(new ChatResponse("Hello there!", "sess-1", "CONVERSATION"));

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest("huh", "sess-1"), "user-1");

    assertEquals("Hello there!", resp.message());
    verify(conversationAgent).process(eq("conversation:sess-1"), anyString());
  }

  @Test
  void route_contentAnalysisIntent_withoutDocument_reclassifiesToConversation() {
    when(intentClassifier.classify("analyze this")).thenReturn("CONTENT_ANALYSIS");
    when(conversationAgent.process(eq("conversation:sess-1"), anyString()))
        .thenReturn("Explain result");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenAnswer(
            inv -> {
              AgenticScope scope = inv.getArgument(0);
              String intent = scope.readState("intent", "");
              return new ChatResponse("Explain result", "sess-1", intent);
            });

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest("analyze this", "sess-1"), "user-1");

    assertEquals("CONVERSATION", resp.agentType());
    verify(conversationAgent).process(eq("conversation:sess-1"), anyString());
    verify(vectorDBService, never())
        .retrieveRelevantContext(anyString(), eq(8), any(Predicate.class));
  }

  @Test
  void route_assessmentIntent_enrichesWithContext() {
    when(conversationRepository.lastUploadedDocumentId("user-1", "sess-1")).thenReturn("doc-9");
    when(intentClassifier.classify("quiz me")).thenReturn("ASSESSMENT");
    when(vectorDBService.retrieveRelevantContext(anyString(), eq(3), eq("doc:doc-9")))
        .thenReturn(java.util.List.of("context from qdrant"));
    when(questionGenerationAgent.process(
            eq(ChatMemoryKeys.assessment("sess-1")), anyString(), contains("context from qdrant")))
        .thenReturn("Assessment result");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenReturn(new ChatResponse("Assessment result", "sess-1", "ASSESSMENT"));

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest("quiz me", "sess-1"), "user-1");

    assertEquals("Assessment result", resp.message());
    verify(vectorDBService, times(2)).retrieveRelevantContext(anyString(), eq(3), eq("doc:doc-9"));
  }

  @Test
  void route_assessmentIntent_scopesToExplicitContentId() {
    String msg = "Generate assessment for content doc-9";
    when(intentClassifier.classify(msg)).thenReturn("ASSESSMENT");
    when(vectorDBService.retrieveRelevantContext(anyString(), eq(3), eq("doc:doc-9")))
        .thenReturn(java.util.List.of("rome content"));
    when(questionGenerationAgent.process(
            eq(ChatMemoryKeys.assessment("sess-1")), anyString(), contains("rome content")))
        .thenReturn("Rome assessment");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenReturn(new ChatResponse("Rome assessment", "sess-1", "ASSESSMENT"));

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest(msg, "sess-1"), "user-1");

    assertEquals("Rome assessment", resp.message());
    verify(vectorDBService, times(2)).retrieveRelevantContext(anyString(), eq(3), eq("doc:doc-9"));
    verify(questionGenerationAgent)
        .process(eq(ChatMemoryKeys.assessment("sess-1")), anyString(), anyString());
  }

  @Test
  void route_insightIntent_feedsRealAnalytics() {
    when(intentClassifier.classify("my progress")).thenReturn("INSIGHT");
    when(insightDataService.buildContext("user-1", "sess-1"))
        .thenReturn("- Messages in this session: 12");
    when(insightAgent.process(
            eq(ChatMemoryKeys.insight("sess-1")), contains("- Messages in this session: 12")))
        .thenReturn("Insight result");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenReturn(new ChatResponse("Insight result", "sess-1", "INSIGHT"));

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest("my progress", "sess-1"), "user-1");

    assertEquals("Insight result", resp.message());
    verify(insightAgent)
        .process(eq(ChatMemoryKeys.insight("sess-1")), contains("- Messages in this session: 12"));
    verify(insightDataService).buildContext("user-1", "sess-1");
  }

  @Test
  void route_insightIntent() {
    when(intentClassifier.classify("my progress")).thenReturn("INSIGHT");
    when(insightAgent.process(eq(ChatMemoryKeys.insight("sess-1")), anyString()))
        .thenReturn("Insight result");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenReturn(new ChatResponse("Insight result", "sess-1", "INSIGHT"));

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest("my progress", "sess-1"), "user-1");

    assertEquals("Insight result", resp.message());
    verify(kafkaEventPublisher).publishInsightGenerated(eq("user-1"), eq("sess-1"), anyString());
  }

  @Test
  void route_videoSearchIntent_returnsRealLinksWithoutRouter() {
    when(intentClassifier.classify("Give me a youtube link to Neural networks by 3b1b"))
        .thenReturn("VIDEO_SEARCH");
    when(youTubeSearchService.extractQuery("Give me a youtube link to Neural networks by 3b1b"))
        .thenReturn("Neural networks by 3b1b");
    when(youTubeSearchService.search("Neural networks by 3b1b"))
        .thenReturn(
            java.util.List.of(
                new YouTubeSearchService.VideoResult(
                    "But what is a neural network?", "aircAruvnKk")));
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenAnswer(
            inv -> {
              AgenticScope scope = inv.getArgument(0);
              String msg = scope.readState("response", "");
              return new ChatResponse(msg, "sess-1", "VIDEO_SEARCH");
            });

    OrchestratorService svc = buildService();
    ChatResponse resp =
        svc.route(
            new ChatRequest("Give me a youtube link to Neural networks by 3b1b", "sess-1"),
            "user-1");

    assertTrue(resp.message().contains("https://www.youtube.com/watch?v=aircAruvnKk"));
    assertEquals("VIDEO_SEARCH", resp.agentType());
    verify(conversationAgent, never()).process(eq("conversation:sess-1"), anyString());
    verify(profilingAgent).process(eq("profiling:sess-1"), anyString());
  }

  @Test
  void route_videoSearchIntent_emptyResults_returnsCannedMessage() {
    when(intentClassifier.classify("video please")).thenReturn("VIDEO_SEARCH");
    when(youTubeSearchService.extractQuery("video please")).thenReturn("video please");
    when(youTubeSearchService.search("video please")).thenReturn(java.util.List.of());
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenAnswer(
            inv -> {
              AgenticScope scope = inv.getArgument(0);
              String msg = scope.readState("response", "");
              return new ChatResponse(msg, "sess-1", "VIDEO_SEARCH");
            });

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest("video please", "sess-1"), "user-1");

    assertTrue(resp.message().contains("couldn't find any YouTube videos"));
    assertEquals("VIDEO_SEARCH", resp.agentType());
    verify(conversationAgent, never()).process(anyString(), anyString());
  }

  @Test
  void route_videoKeywordUsesClassifier() {
    when(intentClassifier.classify("Give youtube videos")).thenReturn("VIDEO_SEARCH");
    when(youTubeSearchService.extractQuery("Give youtube videos")).thenReturn("");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenAnswer(
            inv -> {
              AgenticScope scope = inv.getArgument(0);
              String msg = scope.readState("response", "");
              return new ChatResponse(msg, "sess-1", "VIDEO_SEARCH");
            });

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest("Give youtube videos", "sess-1"), "user-1");

    assertTrue(resp.message().contains("couldn't find any YouTube videos"));
    assertEquals("VIDEO_SEARCH", resp.agentType());
    verify(intentClassifier).classify("Give youtube videos");
    verify(conversationAgent, never()).process(anyString(), anyString());
  }

  @Test
  void route_explicitVideoLinkShortCircuitsClassifier() {
    String msg = "Check this out: https://youtu.be/aircAruvnKk";
    when(youTubeSearchService.extractQuery(msg)).thenReturn("");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenAnswer(
            inv -> {
              AgenticScope scope = inv.getArgument(0);
              String state = scope.readState("response", "");
              return new ChatResponse(state, "sess-1", "VIDEO_SEARCH");
            });

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest(msg, "sess-1"), "user-1");

    assertEquals("VIDEO_SEARCH", resp.agentType());
    verify(intentClassifier, never()).classify(anyString());
    verify(conversationAgent, never()).process(anyString(), anyString());
  }

  @Test
  void route_youtubeDotComLinkShortCircuitsClassifier() {
    String msg = "www.youtube.com/watch?v=abc123";
    when(youTubeSearchService.extractQuery(msg)).thenReturn("");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenAnswer(
            inv -> {
              AgenticScope scope = inv.getArgument(0);
              String state = scope.readState("response", "");
              return new ChatResponse(state, "sess-1", "VIDEO_SEARCH");
            });

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest(msg, "sess-1"), "user-1");

    assertEquals("VIDEO_SEARCH", resp.agentType());
    verify(intentClassifier, never()).classify(anyString());
    verify(conversationAgent, never()).process(anyString(), anyString());
  }

  @Test
  void route_mentionsYoutubeWithoutLink_UsesClassifier() {
    when(intentClassifier.classify("Tell me about youtube ads")).thenReturn("CONVERSATION");
    when(conversationAgent.process(eq("conversation:sess-1"), anyString()))
        .thenReturn("Here's an explanation of YouTube ads.");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenAnswer(
            inv -> {
              AgenticScope scope = inv.getArgument(0);
              String state = scope.readState("response", "");
              return new ChatResponse(state, "sess-1", "CONVERSATION");
            });

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest("Tell me about youtube ads", "sess-1"), "user-1");

    assertEquals("CONVERSATION", resp.agentType());
    verify(intentClassifier).classify("Tell me about youtube ads");
    verify(youTubeSearchService, never()).search(anyString());
  }

  @Test
  void route_videoKeyword_usesHistoryTopicWhenNoExplicitTopic() {
    when(intentClassifier.classify("Ok\nGive youtube videos")).thenReturn("VIDEO_SEARCH");
    when(conversationRepository.getHistory("user-1", "sess-1"))
        .thenReturn(
            new ChatHistory(
                "sess-1",
                java.util.List.of(
                    new ChatHistory.ChatMessage("user", "Master plan for git", null),
                    new ChatHistory.ChatMessage(
                        "assistant", "Master plan given", "CONVERSATION"))));
    when(youTubeSearchService.extractQuery("Ok\nGive youtube videos")).thenReturn("");
    when(youTubeSearchService.extractQuery("Master plan for git")).thenReturn("git");
    when(youTubeSearchService.search("git"))
        .thenReturn(
            java.util.List.of(new YouTubeSearchService.VideoResult("Git tutorial", "dG2kXvT4vX4")));
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenAnswer(
            inv -> {
              AgenticScope scope = inv.getArgument(0);
              String msg = scope.readState("response", "");
              return new ChatResponse(msg, "sess-1", "VIDEO_SEARCH");
            });

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest("Ok\nGive youtube videos", "sess-1"), "user-1");

    assertTrue(resp.message().contains("watch?v=dG2kXvT4vX4"));
    verify(youTubeSearchService).search("git");
    verify(intentClassifier).classify("Ok\nGive youtube videos");
    verify(conversationAgent, never()).process(anyString(), anyString());
  }

  @Test
  void route_videoKeyword_usesMemoryTopicWhenNoExplicitTopic() {
    when(intentClassifier.classify("Ok\nGive youtube videos")).thenReturn("VIDEO_SEARCH");
    when(chatMemoryStore.getMessages(ChatMemoryKeys.conversation("sess-1")))
        .thenReturn(java.util.List.of(UserMessage.from("Learn about git")));
    when(youTubeSearchService.extractQuery("Ok\nGive youtube videos")).thenReturn("");
    when(youTubeSearchService.extractQuery("Learn about git")).thenReturn("git");
    when(youTubeSearchService.search("git"))
        .thenReturn(
            java.util.List.of(new YouTubeSearchService.VideoResult("Git tutorial", "dG2kXvT4vX4")));
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenAnswer(
            inv -> {
              AgenticScope scope = inv.getArgument(0);
              String msg = scope.readState("response", "");
              return new ChatResponse(msg, "sess-1", "VIDEO_SEARCH");
            });

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest("Ok\nGive youtube videos", "sess-1"), "user-1");

    assertTrue(resp.message().contains("watch?v=dG2kXvT4vX4"));
    verify(youTubeSearchService).search("git");
    verify(intentClassifier).classify("Ok\nGive youtube videos");
    verify(conversationAgent, never()).process(anyString(), anyString());
  }

  @Test
  void route_classifiedVideoSearch_bareRequest_usesHistoryTopic() {
    when(intentClassifier.classify("Give me videos")).thenReturn("VIDEO_SEARCH");
    when(conversationRepository.getHistory("user-1", "sess-1"))
        .thenReturn(
            new ChatHistory(
                "sess-1",
                java.util.List.of(
                    new ChatHistory.ChatMessage("user", "Master plan for git", null))));
    when(youTubeSearchService.extractQuery("Give me videos")).thenReturn("");
    when(youTubeSearchService.extractQuery("Master plan for git")).thenReturn("git");
    when(youTubeSearchService.search("git"))
        .thenReturn(
            java.util.List.of(new YouTubeSearchService.VideoResult("Git tutorial", "dG2kXvT4vX4")));
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenAnswer(
            inv -> {
              AgenticScope scope = inv.getArgument(0);
              String msg = scope.readState("response", "");
              return new ChatResponse(msg, "sess-1", "VIDEO_SEARCH");
            });

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest("Give me videos", "sess-1"), "user-1");

    assertTrue(resp.message().contains("watch?v=dG2kXvT4vX4"));
    assertEquals("VIDEO_SEARCH", resp.agentType());
    verify(intentClassifier).classify("Give me videos");
    verify(youTubeSearchService).search("git");
    verify(conversationAgent, never()).process(anyString(), anyString());
  }

  @Test
  void route_classifiedVideoSearch_withTopicAndHistory_mergesContext() {
    when(intentClassifier.classify("Give me videos on branch management"))
        .thenReturn("VIDEO_SEARCH");
    when(conversationRepository.getHistory("user-1", "sess-1"))
        .thenReturn(
            new ChatHistory(
                "sess-1",
                java.util.List.of(
                    new ChatHistory.ChatMessage("user", "Master plan for git", null))));
    when(youTubeSearchService.extractQuery("Give me videos on branch management"))
        .thenReturn("branch management");
    when(youTubeSearchService.extractQuery("Master plan for git")).thenReturn("git");
    when(youTubeSearchService.search("branch management git"))
        .thenReturn(
            java.util.List.of(
                new YouTubeSearchService.VideoResult("Git branches explained", "abc123")));
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenAnswer(
            inv -> {
              AgenticScope scope = inv.getArgument(0);
              String msg = scope.readState("response", "");
              return new ChatResponse(msg, "sess-1", "VIDEO_SEARCH");
            });

    OrchestratorService svc = buildService();
    ChatResponse resp =
        svc.route(new ChatRequest("Give me videos on branch management", "sess-1"), "user-1");

    assertTrue(resp.message().contains("watch?v=abc123"));
    verify(youTubeSearchService).search("branch management git");
    verify(conversationAgent, never()).process(anyString(), anyString());
  }

  @Test
  void route_videoSearch_noTopicInContext_returnsCannedMessage() {
    when(intentClassifier.classify("Give youtube videos")).thenReturn("VIDEO_SEARCH");
    when(conversationRepository.getHistory("user-1", "sess-1"))
        .thenReturn(new ChatHistory("sess-1", java.util.List.of()));
    when(youTubeSearchService.extractQuery("Give youtube videos")).thenReturn("");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenAnswer(
            inv -> {
              AgenticScope scope = inv.getArgument(0);
              String msg = scope.readState("response", "");
              return new ChatResponse(msg, "sess-1", "VIDEO_SEARCH");
            });

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest("Give youtube videos", "sess-1"), "user-1");

    assertTrue(resp.message().contains("couldn't find any YouTube videos"));
    verify(intentClassifier).classify("Give youtube videos");
    verify(youTubeSearchService, never()).search(anyString());
  }

  @Test
  void route_videoSearch_intentIsVerifiedAndResearchesWhenRejected() {
    when(intentClassifier.classify("Give me a youtube link to Neural networks by 3b1b"))
        .thenReturn("VIDEO_SEARCH");
    when(youTubeSearchService.extractQuery("Give me a youtube link to Neural networks by 3b1b"))
        .thenReturn("Neural networks by 3b1b");
    when(youTubeSearchService.search("Neural networks by 3b1b"))
        .thenReturn(
            java.util.List.of(
                new YouTubeSearchService.VideoResult(
                    "But what is a neural network?", "aircAruvnKk")))
        .thenReturn(
            java.util.List.of(new YouTubeSearchService.VideoResult("Better video", "better123")));
    when(responseVerifierAgent.verify(
            eq("Give me a youtube link to Neural networks by 3b1b"), anyString(), anyString()))
        .thenReturn("{\"verdict\": \"NEEDS_REWRITE\", \"reason\": \"links look stale\"}")
        .thenReturn("{\"verdict\": \"ACCEPT\", \"reason\": \"valid links\"}");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenAnswer(
            inv -> {
              AgenticScope scope = inv.getArgument(0);
              String msg = scope.readState("response", "");
              return new ChatResponse(msg, "sess-1", "VIDEO_SEARCH");
            });

    OrchestratorService svc = buildService();
    ChatResponse resp =
        svc.route(
            new ChatRequest("Give me a youtube link to Neural networks by 3b1b", "sess-1"),
            "user-1");

    assertTrue(resp.message().contains("watch?v=better123"));
    verify(youTubeSearchService, times(2)).search("Neural networks by 3b1b");
    verify(conversationAgent, never()).process(eq("conversation:sess-1"), anyString());
  }

  @Test
  void route_sanitizesAgentResponseLinks() {
    String raw = "Here: https://www.youtube.com/watch?v=your_video_ done";
    when(intentClassifier.classify("link please")).thenReturn("CONVERSATION");
    when(conversationAgent.process(eq("conversation:sess-1"), anyString())).thenReturn(raw);
    when(responseVerifierAgent.verify(eq("link please"), anyString(), anyString()))
        .thenReturn("{\"verdict\": \"ACCEPT\", \"reason\": \"links ok\"}");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenReturn(new ChatResponse(raw, "sess-1", "CONVERSATION"));

    OrchestratorService svc = buildService();
    svc.route(new ChatRequest("link please", "sess-1"), "user-1");

    verify(youTubeLinkValidator).sanitize(raw);
  }

  @Test
  void route_verifier_acceptedResponseIsPassedThrough() {
    when(intentClassifier.classify("what is a neural network")).thenReturn("CONVERSATION");
    when(conversationAgent.process(eq("conversation:sess-1"), anyString()))
        .thenReturn("A neural network is a function approximator.");
    when(responseVerifierAgent.verify(eq("what is a neural network"), anyString(), anyString()))
        .thenReturn("{\"verdict\": \"ACCEPT\", \"reason\": \"on topic and accurate\"}");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenAnswer(
            inv -> {
              AgenticScope scope = inv.getArgument(0);
              String msg = scope.readState("response", "");
              return new ChatResponse(msg, "sess-1", "CONVERSATION");
            });

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest("what is a neural network", "sess-1"), "user-1");

    assertEquals("A neural network is a function approximator.", resp.message());
    verify(responseVerifierAgent).verify(eq("what is a neural network"), anyString(), anyString());
    verify(conversationAgent, times(1)).process(eq("conversation:sess-1"), anyString());
  }

  @Test
  void route_verifier_rejectedResponseRegeneratesOnceAndSends() {
    when(intentClassifier.classify("what is a neural network")).thenReturn("CONVERSATION");
    when(conversationAgent.process(eq("conversation:sess-1"), anyString()))
        .thenReturn("Off-topic rambling.")
        .thenReturn("A neural network is a function approximator.");
    when(responseVerifierAgent.verify(
            eq("what is a neural network"), anyString(), eq("Off-topic rambling.")))
        .thenReturn("{\"verdict\": \"NEEDS_REWRITE\", \"reason\": \"off-topic\"}");
    when(responseVerifierAgent.verify(
            eq("what is a neural network"),
            anyString(),
            eq("A neural network is a function approximator.")))
        .thenReturn("{\"verdict\": \"ACCEPT\", \"reason\": \"accurate\"}");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenAnswer(
            inv -> {
              AgenticScope scope = inv.getArgument(0);
              String msg = scope.readState("response", "");
              return new ChatResponse(msg, "sess-1", "CONVERSATION");
            });

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest("what is a neural network", "sess-1"), "user-1");

    assertEquals("A neural network is a function approximator.", resp.message());
    verify(conversationAgent, times(2)).process(eq("conversation:sess-1"), anyString());
    verify(responseVerifierAgent, times(2))
        .verify(eq("what is a neural network"), anyString(), anyString());
  }

  @Test
  void route_verifier_nullVerdict_failsClosedAndRegenerates() {
    when(intentClassifier.classify("what is a neural network")).thenReturn("CONVERSATION");
    when(responseVerifierAgent.verify(eq("what is a neural network"), anyString(), anyString()))
        .thenReturn(null);
    when(conversationAgent.process(eq("conversation:sess-1"), anyString()))
        .thenReturn("Some answer.")
        .thenReturn("A neural network is a function approximator.");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenAnswer(
            inv -> {
              AgenticScope scope = inv.getArgument(0);
              String msg = scope.readState("response", "");
              return new ChatResponse(msg, "sess-1", "CONVERSATION");
            });

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest("what is a neural network", "sess-1"), "user-1");

    assertTrue(resp.message().contains("A neural network is a function approximator."));
    verify(conversationAgent, times(2)).process(eq("conversation:sess-1"), anyString());
    verify(responseVerifierAgent, times(2))
        .verify(eq("what is a neural network"), anyString(), anyString());
  }

  @Test
  void route_verifier_malformedJson_failsClosedAndRegenerates() {
    when(intentClassifier.classify("what is a neural network")).thenReturn("CONVERSATION");
    when(conversationAgent.process(eq("conversation:sess-1"), anyString()))
        .thenReturn("Some answer.")
        .thenReturn("A neural network is a function approximator.");
    when(responseVerifierAgent.verify(eq("what is a neural network"), anyString(), anyString()))
        .thenReturn("this is not json");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenAnswer(
            inv -> {
              AgenticScope scope = inv.getArgument(0);
              String msg = scope.readState("response", "");
              return new ChatResponse(msg, "sess-1", "CONVERSATION");
            });

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest("what is a neural network", "sess-1"), "user-1");

    assertTrue(resp.message().contains("A neural network is a function approximator."));
    verify(conversationAgent, times(2)).process(eq("conversation:sess-1"), anyString());
  }

  @Test
  void route_verifier_throws_failsClosedAndRegenerates() {
    when(intentClassifier.classify("what is a neural network")).thenReturn("CONVERSATION");
    when(conversationAgent.process(eq("conversation:sess-1"), anyString()))
        .thenReturn("Some answer.")
        .thenReturn("A neural network is a function approximator.");
    when(responseVerifierAgent.verify(eq("what is a neural network"), anyString(), anyString()))
        .thenThrow(new RuntimeException("LLM timeout"));
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenAnswer(
            inv -> {
              AgenticScope scope = inv.getArgument(0);
              String msg = scope.readState("response", "");
              return new ChatResponse(msg, "sess-1", "CONVERSATION");
            });

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest("what is a neural network", "sess-1"), "user-1");

    assertTrue(resp.message().contains("A neural network is a function approximator."));
    verify(conversationAgent, times(2)).process(eq("conversation:sess-1"), anyString());
  }

  @Test
  void route_handlesVectorDbFailureGracefully() {
    when(conversationRepository.lastUploadedDocumentId("user-1", "sess-1")).thenReturn("doc-9");
    when(intentClassifier.classify("analyze")).thenReturn("CONTENT_ANALYSIS");
    when(vectorDBService.retrieveRelevantContext(anyString(), eq(8), eq("doc:doc-9")))
        .thenThrow(new RuntimeException("Qdrant down"));
    when(contentAnalysisAgent.process(eq(ChatMemoryKeys.analysis("sess-1")), anyString()))
        .thenReturn("Analysis result (no context)");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenReturn(new ChatResponse("Analysis result (no context)", "sess-1", "CONTENT_ANALYSIS"));

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest("analyze", "sess-1"), "user-1");

    assertNotNull(resp);
    verify(responseComposer).compose(any(AgenticScope.class), eq("sess-1"));
  }

  @Test
  void route_uploadFile_resolvesContent() {
    String uploadMsg = "Analyze the uploaded file: doc-1";
    when(intentClassifier.classify(uploadMsg)).thenReturn("CONTENT_ANALYSIS");
    when(contentDocumentService.resolveContent("doc-1"))
        .thenReturn("File: notes.pdf\n\nContent:\nsample text");
    when(contentAnalysisAgent.process(eq(ChatMemoryKeys.analysis("upload-user-1")), anyString()))
        .thenReturn("Analysis complete");
    when(responseComposer.compose(any(AgenticScope.class), eq("upload-user-1")))
        .thenReturn(new ChatResponse("Analysis complete", "upload-user-1", "CONTENT_ANALYSIS"));

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest(uploadMsg, "upload-user-1"), "user-1");

    assertNotNull(resp);
    verify(contentDocumentService).resolveContent("doc-1");
    verify(contentAnalysisAgent).process(eq(ChatMemoryKeys.analysis("upload-user-1")), anyString());
  }

  @Test
  void route_docReference_question_routesToAssessmentWithContext() {
    String msg = "Questions based on the document";
    when(intentClassifier.classify(msg)).thenReturn("ASSESSMENT");
    when(contentDocumentService.resolveRecentDocumentId("user-1", "sess-1")).thenReturn("doc-9");
    when(vectorDBService.retrieveRelevantContext(anyString(), eq(3), eq("doc:doc-9")))
        .thenReturn(java.util.List.of("rome context"));
    when(questionGenerationAgent.process(
            eq(ChatMemoryKeys.assessment("sess-1")), anyString(), contains("rome context")))
        .thenReturn("Rome questions");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenReturn(new ChatResponse("Rome questions", "sess-1", "ASSESSMENT"));

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest(msg, "sess-1"), "user-1");

    assertEquals("Rome questions", resp.message());
    assertEquals("ASSESSMENT", resp.agentType());
    verify(intentClassifier).classify(msg);
    verify(conversationAgent, never()).process(anyString(), anyString());
    verify(questionGenerationAgent)
        .process(eq(ChatMemoryKeys.assessment("sess-1")), anyString(), anyString());
  }

  @Test
  void route_docReference_question_fallsBackToUserWideDocument() {
    String msg = "Questions based on the document";
    when(intentClassifier.classify(msg)).thenReturn("ASSESSMENT");
    when(contentDocumentService.resolveRecentDocumentId("user-1", "sess-1")).thenReturn(null);
    when(conversationRepository.lastUploadedDocumentId("user-1", "sess-1")).thenReturn(null);
    when(contentDocumentService.resolveRecentDocumentId("user-1", null)).thenReturn("doc-9");
    when(vectorDBService.retrieveRelevantContext(anyString(), eq(3), eq("doc:doc-9")))
        .thenReturn(java.util.List.of("rome context"));
    when(questionGenerationAgent.process(
            eq(ChatMemoryKeys.assessment("sess-1")), anyString(), contains("rome context")))
        .thenReturn("Rome questions");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenReturn(new ChatResponse("Rome questions", "sess-1", "ASSESSMENT"));

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest(msg, "sess-1"), "user-1");

    assertEquals("ASSESSMENT", resp.agentType());
    verify(contentDocumentService, atLeastOnce()).resolveRecentDocumentId("user-1", null);
    verify(questionGenerationAgent)
        .process(eq(ChatMemoryKeys.assessment("sess-1")), anyString(), anyString());
  }

  @Test
  void route_docReference_summarize_routesToContentAnalysis() {
    String msg = "Summarize the uploaded document";
    when(intentClassifier.classify(msg)).thenReturn("CONTENT_ANALYSIS");
    when(contentDocumentService.resolveRecentDocumentId("user-1", "sess-1")).thenReturn("doc-9");
    when(vectorDBService.retrieveRelevantContext(anyString(), eq(8), eq("doc:doc-9")))
        .thenReturn(java.util.List.of("rome context"));
    when(contentAnalysisAgent.process(
            eq(ChatMemoryKeys.analysis("sess-1")), contains("rome context")))
        .thenReturn("Rome summary");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenReturn(new ChatResponse("Rome summary", "sess-1", "CONTENT_ANALYSIS"));

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest(msg, "sess-1"), "user-1");

    assertEquals("CONTENT_ANALYSIS", resp.agentType());
    verify(intentClassifier).classify(msg);
    verify(contentAnalysisAgent).process(eq(ChatMemoryKeys.analysis("sess-1")), anyString());
  }

  @Test
  void route_classifiedAssessment_withoutAnyDocument_reclassifiesToConversation() {
    String msg = "Questions based on the document";
    when(intentClassifier.classify(msg)).thenReturn("ASSESSMENT");
    when(contentDocumentService.resolveRecentDocumentId("user-1", "sess-1")).thenReturn(null);
    when(conversationRepository.lastUploadedDocumentId("user-1", "sess-1")).thenReturn(null);
    when(contentDocumentService.resolveRecentDocumentId("user-1", null)).thenReturn(null);
    when(conversationAgent.process(eq("conversation:sess-1"), anyString()))
        .thenReturn("Let me help you with that.");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenAnswer(
            inv -> {
              AgenticScope scope = inv.getArgument(0);
              String msgState = scope.readState("response", "");
              String intent = scope.readState("intent", "");
              return new ChatResponse(msgState, "sess-1", intent);
            });

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest(msg, "sess-1"), "user-1");

    assertEquals("Let me help you with that.", resp.message());
    assertEquals("CONVERSATION", resp.agentType());
    verify(intentClassifier).classify(msg);
    verify(conversationAgent).process(eq("conversation:sess-1"), anyString());
    verify(questionGenerationAgent, never()).process(anyString(), anyString(), anyString());
  }

  @Test
  void assessmentTargetId_ignoresTrailingParams() {
    assertEquals(
        "doc-1", OrchestratorService.assessmentTargetId("Generate assessment for content doc-1"));
    assertEquals(
        "doc-1",
        OrchestratorService.assessmentTargetId(
            "Generate assessment for content doc-1 | questions=5 | difficulty=medium"));
    assertEquals(
        "doc-1",
        OrchestratorService.assessmentTargetId(
            "Generate assessment for content  doc-1  | questions=10| difficulty=hard "));
    assertEquals("", OrchestratorService.assessmentTargetId("Generate assessment for content "));
  }

  private OrchestratorService buildService() {
    OrchestratorService svc = new OrchestratorService();
    svc.intentClassifier = intentClassifier;
    svc.profilingAgent = profilingAgent;
    svc.responseComposer = responseComposer;
    svc.conversationAgent = conversationAgent;
    svc.contentAnalysisAgent = contentAnalysisAgent;
    svc.questionGenerationAgent = questionGenerationAgent;
    svc.responseVerifierAgent = responseVerifierAgent;
    svc.insightAgent = insightAgent;
    svc.profilingService = profilingService;
    svc.conversationRepository = conversationRepository;
    svc.vectorDBService = vectorDBService;
    svc.contentDocumentService = contentDocumentService;
    svc.insightDataService = insightDataService;
    svc.kafkaEventPublisher = kafkaEventPublisher;
    svc.youTubeLinkValidator = youTubeLinkValidator;
    svc.youTubeSearchService = youTubeSearchService;
    svc.chatMemoryStore = chatMemoryStore;
    svc.objectMapper = new ObjectMapper();
    lenient()
        .when(youTubeLinkValidator.sanitize(anyString()))
        .thenAnswer(inv -> inv.getArgument(0));
    return svc;
  }
}
