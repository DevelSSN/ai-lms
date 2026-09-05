package com.ailms.orchestrator.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ailms.common.dto.ChatRequest;
import com.ailms.common.dto.ChatResponse;
import com.ailms.orchestrator.agent.ContentAnalysisAgent;
import com.ailms.orchestrator.agent.ConversationAgent;
import com.ailms.orchestrator.agent.InsightAgent;
import com.ailms.orchestrator.agent.IntentClassifier;
import com.ailms.orchestrator.agent.ProfilingAgent;
import com.ailms.orchestrator.agent.ProactiveFollowUpAgent;
import com.ailms.orchestrator.agent.QuestionGenerationAgent;
import com.ailms.orchestrator.agent.ResponseComposer;
import com.ailms.orchestrator.repository.ConversationRepository;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.data.message.UserMessage;
import java.util.function.Predicate;
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
  @Mock InsightAgent insightAgent;
  @Mock ProactiveFollowUpAgent proactiveFollowUpAgent;
  @Mock ProfilingService profilingService;
  @Mock ConversationRepository conversationRepository;
  @Mock VectorDBService vectorDBService;
  @Mock ContentDocumentService contentDocumentService;
  @Mock KafkaEventPublisher kafkaEventPublisher;
  @Mock YouTubeLinkValidator youTubeLinkValidator;
  @Mock YouTubeSearchService youTubeSearchService;
  @Mock RedisChatMemoryStore chatMemoryStore;

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
            eq("conversation:sess-1"), anyString(), contains("context from qdrant")))
        .thenReturn("Assessment result");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenReturn(new ChatResponse("Assessment result", "sess-1", "ASSESSMENT"));

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest("quiz me", "sess-1"), "user-1");

    assertEquals("Assessment result", resp.message());
    verify(vectorDBService, times(2))
        .retrieveRelevantContext(anyString(), eq(3), eq("doc:doc-9"));
  }

  @Test
  void route_assessmentIntent_scopesToExplicitContentId() {
    String msg = "Generate assessment for content doc-9";
    when(intentClassifier.classify(msg)).thenReturn("ASSESSMENT");
    when(vectorDBService.retrieveRelevantContext(anyString(), eq(3), eq("doc:doc-9")))
        .thenReturn(java.util.List.of("rome content"));
    when(questionGenerationAgent.process(
            eq("conversation:sess-1"), anyString(), contains("rome content")))
        .thenReturn("Rome assessment");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenReturn(new ChatResponse("Rome assessment", "sess-1", "ASSESSMENT"));

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest(msg, "sess-1"), "user-1");

    assertEquals("Rome assessment", resp.message());
    verify(vectorDBService, times(2)).retrieveRelevantContext(anyString(), eq(3), eq("doc:doc-9"));
    verify(questionGenerationAgent).process(eq("conversation:sess-1"), anyString(), anyString());
  }

  @Test
  void route_insightIntent() {
    when(intentClassifier.classify("my progress")).thenReturn("INSIGHT");
    when(insightAgent.process(eq("conversation:sess-1"), anyString())).thenReturn("Insight result");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenReturn(new ChatResponse("Insight result", "sess-1", "INSIGHT"));

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest("my progress", "sess-1"), "user-1");

    assertEquals("Insight result", resp.message());
    verify(kafkaEventPublisher).publishInsightGenerated(eq("user-1"), eq("sess-1"), anyString());
  }

  @Test
  void route_videoSearchIntent_returnsRealLinksWithoutRouter() {
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
  void route_videoKeywordShortCircuitsClassifier() {
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
    verify(intentClassifier, never()).classify(anyString());
    verify(conversationAgent, never()).process(anyString(), anyString());
  }

  @Test
  void route_videoKeyword_usesMemoryTopicWhenNoExplicitTopic() {
    when(chatMemoryStore.getMessages("conversation:sess-1"))
        .thenReturn(java.util.List.of(UserMessage.from("Learn about git")));
    when(youTubeSearchService.extractQuery("Ok\nGive youtube videos")).thenReturn("");
    when(youTubeSearchService.search("Learn about git"))
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
    verify(youTubeSearchService).search("Learn about git");
    verify(intentClassifier, never()).classify(anyString());
    verify(conversationAgent, never()).process(anyString(), anyString());
  }

  @Test
  void route_sanitizesAgentResponseLinks() {
    String raw = "Here: https://www.youtube.com/watch?v=your_video_ done";
    when(intentClassifier.classify("link please")).thenReturn("CONVERSATION");
    when(conversationAgent.process(eq("conversation:sess-1"), anyString())).thenReturn(raw);
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenReturn(new ChatResponse(raw, "sess-1", "CONVERSATION"));

    OrchestratorService svc = buildService();
    svc.route(new ChatRequest("link please", "sess-1"), "user-1");

    verify(youTubeLinkValidator).sanitize(raw);
  }

  @Test
  void route_handlesVectorDbFailureGracefully() {
    when(conversationRepository.lastUploadedDocumentId("user-1", "sess-1")).thenReturn("doc-9");
    when(intentClassifier.classify("analyze")).thenReturn("CONTENT_ANALYSIS");
    when(vectorDBService.retrieveRelevantContext(anyString(), eq(8), eq("doc:doc-9")))
        .thenThrow(new RuntimeException("Qdrant down"));
    when(contentAnalysisAgent.process(eq("conversation:sess-1"), anyString()))
        .thenReturn("Analysis result (no context)");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenReturn(new ChatResponse("Analysis result (no context)", "sess-1", "CONTENT_ANALYSIS"));

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest("analyze", "sess-1"), "user-1");

    assertNotNull(resp);
    verify(responseComposer).compose(any(AgenticScope.class), eq("sess-1"));
  }

  @Test
  void generateProactiveMessage_callsAgent() {
    when(proactiveFollowUpAgent.generate("context")).thenReturn("Follow up message");

    OrchestratorService svc = buildService();
    String msg = svc.generateProactiveMessage("user-1", "context");

    assertEquals("Follow up message", msg);
    verify(proactiveFollowUpAgent).generate("context");
  }

  @Test
  void route_uploadFile_resolvesContent() {
    String uploadMsg = "Analyze the uploaded file: doc-1";
    when(intentClassifier.classify(uploadMsg)).thenReturn("CONTENT_ANALYSIS");
    when(contentDocumentService.resolveContent("doc-1"))
        .thenReturn("File: notes.pdf\n\nContent:\nsample text");
    when(contentAnalysisAgent.process(eq("conversation:upload-user-1"), anyString()))
        .thenReturn("Analysis complete");
    when(responseComposer.compose(any(AgenticScope.class), eq("upload-user-1")))
        .thenReturn(new ChatResponse("Analysis complete", "upload-user-1", "CONTENT_ANALYSIS"));

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest(uploadMsg, "upload-user-1"), "user-1");

    assertNotNull(resp);
    verify(contentDocumentService).resolveContent("doc-1");
    verify(contentAnalysisAgent).process(eq("conversation:upload-user-1"), anyString());
  }

  @Test
  void route_docReference_question_routesToAssessmentWithContext() {
    String msg = "Questions based on the document";
    when(contentDocumentService.resolveRecentDocumentId("user-1", "sess-1")).thenReturn("doc-9");
    when(vectorDBService.retrieveRelevantContext(anyString(), eq(3), eq("doc:doc-9")))
        .thenReturn(java.util.List.of("rome context"));
    when(questionGenerationAgent.process(
            eq("conversation:sess-1"), anyString(), contains("rome context")))
        .thenReturn("Rome questions");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenReturn(new ChatResponse("Rome questions", "sess-1", "ASSESSMENT"));

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest(msg, "sess-1"), "user-1");

    assertEquals("Rome questions", resp.message());
    assertEquals("ASSESSMENT", resp.agentType());
    verify(intentClassifier, never()).classify(anyString());
    verify(conversationAgent, never()).process(anyString(), anyString());
    verify(questionGenerationAgent).process(eq("conversation:sess-1"), anyString(), anyString());
  }

  @Test
  void route_docReference_question_fallsBackToUserWideDocument() {
    String msg = "Questions based on the document";
    when(contentDocumentService.resolveRecentDocumentId("user-1", "sess-1")).thenReturn(null);
    when(conversationRepository.lastUploadedDocumentId("user-1", "sess-1")).thenReturn(null);
    when(contentDocumentService.resolveRecentDocumentId("user-1", null)).thenReturn("doc-9");
    when(vectorDBService.retrieveRelevantContext(anyString(), eq(3), eq("doc:doc-9")))
        .thenReturn(java.util.List.of("rome context"));
    when(questionGenerationAgent.process(
            eq("conversation:sess-1"), anyString(), contains("rome context")))
        .thenReturn("Rome questions");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenReturn(new ChatResponse("Rome questions", "sess-1", "ASSESSMENT"));

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest(msg, "sess-1"), "user-1");

    assertEquals("ASSESSMENT", resp.agentType());
    verify(contentDocumentService, atLeastOnce()).resolveRecentDocumentId("user-1", null);
    verify(questionGenerationAgent).process(eq("conversation:sess-1"), anyString(), anyString());
  }

  @Test
  void route_docReference_summarize_routesToContentAnalysis() {
    String msg = "Summarize the uploaded document";
    when(contentDocumentService.resolveRecentDocumentId("user-1", "sess-1")).thenReturn("doc-9");
    when(vectorDBService.retrieveRelevantContext(anyString(), eq(8), eq("doc:doc-9")))
        .thenReturn(java.util.List.of("rome context"));
    when(contentAnalysisAgent.process(eq("conversation:sess-1"), contains("rome context")))
        .thenReturn("Rome summary");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenReturn(new ChatResponse("Rome summary", "sess-1", "CONTENT_ANALYSIS"));

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest(msg, "sess-1"), "user-1");

    assertEquals("CONTENT_ANALYSIS", resp.agentType());
    verify(intentClassifier, never()).classify(anyString());
    verify(contentAnalysisAgent).process(eq("conversation:sess-1"), anyString());
  }

  @Test
  void route_docReference_noDocument_returnsCannedMessage() {
    String msg = "Questions based on the document";
    when(contentDocumentService.resolveRecentDocumentId("user-1", "sess-1")).thenReturn(null);
    when(conversationRepository.lastUploadedDocumentId("user-1", "sess-1")).thenReturn(null);
    when(contentDocumentService.resolveRecentDocumentId("user-1", null)).thenReturn(null);
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenAnswer(
            inv -> {
              AgenticScope scope = inv.getArgument(0);
              String msgState = scope.readState("response", "");
              return new ChatResponse(msgState, "sess-1", "CONVERSATION");
            });

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest(msg, "sess-1"), "user-1");

    assertEquals(
        "I couldn't find an uploaded document in this conversation. "
            + "Upload a file first, and then I can analyze it or generate questions from it.",
        resp.message());
    assertEquals("CONVERSATION", resp.agentType());
    verify(intentClassifier, never()).classify(anyString());
    verify(conversationAgent, never()).process(anyString(), anyString());
    verify(questionGenerationAgent, never()).process(anyString(), anyString(), anyString());
  }

  private OrchestratorService buildService() {
    OrchestratorService svc = new OrchestratorService();
    svc.intentClassifier = intentClassifier;
    svc.profilingAgent = profilingAgent;
    svc.responseComposer = responseComposer;
    svc.conversationAgent = conversationAgent;
    svc.contentAnalysisAgent = contentAnalysisAgent;
    svc.questionGenerationAgent = questionGenerationAgent;
    svc.insightAgent = insightAgent;
    svc.proactiveFollowUpAgent = proactiveFollowUpAgent;
    svc.profilingService = profilingService;
    svc.conversationRepository = conversationRepository;
    svc.vectorDBService = vectorDBService;
    svc.contentDocumentService = contentDocumentService;
    svc.kafkaEventPublisher = kafkaEventPublisher;
    svc.youTubeLinkValidator = youTubeLinkValidator;
    svc.youTubeSearchService = youTubeSearchService;
    svc.chatMemoryStore = chatMemoryStore;
    lenient()
        .when(youTubeLinkValidator.sanitize(anyString()))
        .thenAnswer(inv -> inv.getArgument(0));
    return svc;
  }
}
