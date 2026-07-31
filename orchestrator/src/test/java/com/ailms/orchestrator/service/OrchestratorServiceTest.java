package com.ailms.orchestrator.service;

import com.ailms.common.dto.ChatRequest;
import com.ailms.common.dto.ChatResponse;
import com.ailms.orchestrator.agent.ConversationAgent;
import com.ailms.orchestrator.agent.IntentClassifier;
import com.ailms.orchestrator.agent.OrchestratorRouter;
import com.ailms.orchestrator.agent.ProfilingAgent;
import com.ailms.orchestrator.agent.ResponseComposer;
import com.ailms.orchestrator.repository.ConversationRepository;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrchestratorServiceTest {

  @Mock IntentClassifier intentClassifier;
  @Mock OrchestratorRouter orchestratorRouter;
  @Mock ProfilingAgent profilingAgent;
  @Mock ResponseComposer responseComposer;
  @Mock ConversationAgent conversationAgent;
  @Mock ProfilingService profilingService;
  @Mock ConversationRepository conversationRepository;
  @Mock VectorDBService vectorDBService;
  @Mock ObjectStorageService objectStorage;
  @Mock DocumentParserService documentParser;
  @Mock KafkaEventPublisher kafkaEventPublisher;

  @Test
  void route_conversationIntent() {
    when(intentClassifier.classify("hello")).thenReturn("CONVERSATION");
    when(orchestratorRouter.route(eq("sess-1"), anyString(), eq(""), eq("CONVERSATION")))
        .thenReturn("Hello there!");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenReturn(new ChatResponse("Hello there!", "sess-1", "CONVERSATION"));

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest("hello", "sess-1"), "user-1");

    assertEquals("Hello there!", resp.message());
    verify(profilingService).ensureProfile("user-1");
    verify(profilingAgent).process(eq("sess-1"), anyString());
    verify(conversationRepository).logMessage(anyString(), anyString(), eq("user"), anyString());
  }

  @Test
  void route_normalizesClassifierOutput() {
    when(intentClassifier.classify("hi")).thenReturn("\nconversation. ");
    when(orchestratorRouter.route(eq("sess-1"), anyString(), eq(""), eq("CONVERSATION")))
        .thenReturn("Hello there!");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenReturn(new ChatResponse("Hello there!", "sess-1", "CONVERSATION"));

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest("hi", "sess-1"), "user-1");

    assertEquals("Hello there!", resp.message());
    verify(orchestratorRouter).route(eq("sess-1"), anyString(), eq(""), eq("CONVERSATION"));
  }

  @Test
  void route_defaultsUnrecognizedIntentToConversation() {
    when(intentClassifier.classify("huh")).thenReturn("SOMETHING_ELSE");
    when(orchestratorRouter.route(eq("sess-1"), anyString(), eq(""), eq("CONVERSATION")))
        .thenReturn("Hello there!");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenReturn(new ChatResponse("Hello there!", "sess-1", "CONVERSATION"));

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest("huh", "sess-1"), "user-1");

    assertEquals("Hello there!", resp.message());
    verify(orchestratorRouter).route(eq("sess-1"), anyString(), eq(""), eq("CONVERSATION"));
  }

  @Test
  void route_contentAnalysisIntent() {
    when(intentClassifier.classify("analyze this")).thenReturn("CONTENT_ANALYSIS");
    when(vectorDBService.retrieveRelevantContext(anyString(), eq(3))).thenReturn(java.util.List.of());
    when(orchestratorRouter.route(eq("sess-1"), anyString(), eq(""), eq("CONTENT_ANALYSIS")))
        .thenReturn("Analysis result");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenReturn(new ChatResponse("Analysis result", "sess-1", "CONTENT_ANALYSIS"));

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest("analyze this", "sess-1"), "user-1");

    assertEquals("Analysis result", resp.message());
    verify(vectorDBService).retrieveRelevantContext(anyString(), eq(3));
    verify(vectorDBService).ingestDocument(anyString(), anyString(), anyString());
  }

  @Test
  void route_assessmentIntent_enrichesWithContext() {
    when(intentClassifier.classify("quiz me")).thenReturn("ASSESSMENT");
    when(vectorDBService.retrieveRelevantContext(anyString(), eq(3)))
        .thenReturn(java.util.List.of("context from qdrant"));
    when(orchestratorRouter.route(eq("sess-1"), anyString(), contains("context from qdrant"), eq("ASSESSMENT")))
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
    when(orchestratorRouter.route(eq("sess-1"), anyString(), eq(""), eq("INSIGHT")))
        .thenReturn("Insight result");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenReturn(new ChatResponse("Insight result", "sess-1", "INSIGHT"));

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest("my progress", "sess-1"), "user-1");

    assertEquals("Insight result", resp.message());
    verify(kafkaEventPublisher).publishInsightGenerated(eq("user-1"), eq("sess-1"), anyString());
  }

  @Test
  void route_handlesVectorDbFailureGracefully() {
    when(intentClassifier.classify("analyze")).thenReturn("CONTENT_ANALYSIS");
    when(vectorDBService.retrieveRelevantContext(anyString(), eq(3)))
        .thenThrow(new RuntimeException("Qdrant down"));
    doThrow(new RuntimeException("Qdrant down"))
        .when(vectorDBService).ingestDocument(anyString(), anyString(), anyString());
    when(orchestratorRouter.route(eq("sess-1"), anyString(), eq(""), eq("CONTENT_ANALYSIS")))
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

    when(vectorDBService.retrieveRelevantContext(anyString(), eq(3))).thenReturn(java.util.List.of());
    when(orchestratorRouter.route(eq("upload-user-1"), anyString(), eq(""), eq("CONTENT_ANALYSIS")))
        .thenReturn("Analysis complete");
    when(responseComposer.compose(any(AgenticScope.class), eq("upload-user-1")))
        .thenReturn(new ChatResponse("Analysis complete", "upload-user-1", "CONTENT_ANALYSIS"));

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest(uploadMsg, "upload-user-1"), "user-1");

    assertNotNull(resp);
    verify(orchestratorRouter).route(eq("upload-user-1"), anyString(), eq(""), eq("CONTENT_ANALYSIS"));
  }

  private OrchestratorService buildService() {
    OrchestratorService svc = new OrchestratorService();
    svc.intentClassifier = intentClassifier;
    svc.orchestratorRouter = orchestratorRouter;
    svc.profilingAgent = profilingAgent;
    svc.responseComposer = responseComposer;
    svc.conversationAgent = conversationAgent;
    svc.profilingService = profilingService;
    svc.conversationRepository = conversationRepository;
    svc.vectorDBService = vectorDBService;
    svc.objectStorage = objectStorage;
    svc.documentParser = documentParser;
    svc.kafkaEventPublisher = kafkaEventPublisher;
    return svc;
  }
}
