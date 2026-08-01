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
import com.ailms.orchestrator.agent.QuestionGenerationAgent;
import com.ailms.orchestrator.agent.ResponseComposer;
import com.ailms.orchestrator.repository.ConversationRepository;
import dev.langchain4j.agentic.scope.AgenticScope;
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
  @Mock ProfilingService profilingService;
  @Mock ConversationRepository conversationRepository;
  @Mock VectorDBService vectorDBService;
  @Mock ContentDocumentService contentDocumentService;
  @Mock KafkaEventPublisher kafkaEventPublisher;
  @Mock YouTubeLinkValidator youTubeLinkValidator;
  @Mock YouTubeSearchService youTubeSearchService;

  @Test
  void route_conversationIntent() {
    when(intentClassifier.classify("hello")).thenReturn("CONVERSATION");
    when(conversationAgent.process(eq("conversation:sess-1"), anyString()))
        .thenReturn("Hello there!");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenReturn(new ChatResponse("Hello there!", "sess-1", "CONVERSATION"));

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest("hello", "sess-1"), "user-1");

    assertEquals("Hello there!", resp.message());
    verify(profilingService).ensureProfile("user-1");
    verify(profilingAgent).process(eq("profiling:sess-1"), anyString());
    verify(conversationRepository).logMessage(anyString(), anyString(), eq("user"), anyString());
  }

  @Test
  void route_normalizesClassifierOutput() {
    when(intentClassifier.classify("hi")).thenReturn("\nconversation. ");
    when(conversationAgent.process(eq("conversation:sess-1"), anyString()))
        .thenReturn("Hello there!");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenReturn(new ChatResponse("Hello there!", "sess-1", "CONVERSATION"));

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest("hi", "sess-1"), "user-1");

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
  void route_contentAnalysisIntent() {
    when(intentClassifier.classify("analyze this")).thenReturn("CONTENT_ANALYSIS");
    when(vectorDBService.retrieveRelevantContext(anyString(), eq(8)))
        .thenReturn(java.util.List.of());
    when(contentAnalysisAgent.process(eq("conversation:sess-1"), anyString()))
        .thenReturn("Analysis result");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenReturn(new ChatResponse("Analysis result", "sess-1", "CONTENT_ANALYSIS"));

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest("analyze this", "sess-1"), "user-1");

    assertEquals("Analysis result", resp.message());
    verify(vectorDBService).retrieveRelevantContext(anyString(), eq(8));
  }

  @Test
  void route_assessmentIntent_enrichesWithContext() {
    when(intentClassifier.classify("quiz me")).thenReturn("ASSESSMENT");
    when(vectorDBService.retrieveRelevantContext(anyString(), eq(3)))
        .thenReturn(java.util.List.of("context from qdrant"));
    when(questionGenerationAgent.process(
            eq("conversation:sess-1"), anyString(), contains("context from qdrant")))
        .thenReturn("Assessment result");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenReturn(new ChatResponse("Assessment result", "sess-1", "ASSESSMENT"));

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest("quiz me", "sess-1"), "user-1");

    assertEquals("Assessment result", resp.message());
    verify(vectorDBService, times(2)).retrieveRelevantContext(anyString(), eq(3));
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
  void route_videoSearchIntent_emptyResults_fallsBackToConversation() {
    when(intentClassifier.classify("video please")).thenReturn("VIDEO_SEARCH");
    when(youTubeSearchService.extractQuery("video please")).thenReturn("video please");
    when(youTubeSearchService.search("video please")).thenReturn(java.util.List.of());
    when(conversationAgent.process(eq("conversation:sess-1"), anyString()))
        .thenReturn("Fallback answer");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenReturn(new ChatResponse("Fallback answer", "sess-1", "CONVERSATION"));

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest("video please", "sess-1"), "user-1");

    assertEquals("Fallback answer", resp.message());
    assertEquals("CONVERSATION", resp.agentType());
    verify(conversationAgent).process(eq("conversation:sess-1"), anyString());
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
    when(intentClassifier.classify("analyze")).thenReturn("CONTENT_ANALYSIS");
    when(vectorDBService.retrieveRelevantContext(anyString(), eq(8)))
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
    when(conversationAgent.process(eq("proactive-user-1"), anyString()))
        .thenReturn("Follow up message");

    OrchestratorService svc = buildService();
    String msg = svc.generateProactiveMessage("user-1", "context");

    assertEquals("Follow up message", msg);
    verify(conversationAgent).process(eq("proactive-user-1"), anyString());
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

  private OrchestratorService buildService() {
    OrchestratorService svc = new OrchestratorService();
    svc.intentClassifier = intentClassifier;
    svc.profilingAgent = profilingAgent;
    svc.responseComposer = responseComposer;
    svc.conversationAgent = conversationAgent;
    svc.contentAnalysisAgent = contentAnalysisAgent;
    svc.questionGenerationAgent = questionGenerationAgent;
    svc.insightAgent = insightAgent;
    svc.profilingService = profilingService;
    svc.conversationRepository = conversationRepository;
    svc.vectorDBService = vectorDBService;
    svc.contentDocumentService = contentDocumentService;
    svc.kafkaEventPublisher = kafkaEventPublisher;
    svc.youTubeLinkValidator = youTubeLinkValidator;
    svc.youTubeSearchService = youTubeSearchService;
    lenient()
        .when(youTubeLinkValidator.sanitize(anyString()))
        .thenAnswer(inv -> inv.getArgument(0));
    return svc;
  }
}
