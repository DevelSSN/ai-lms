package com.ailms.orchestrator.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ailms.common.constants.ChatMemoryKeys;
import com.ailms.common.dto.ChatHistory;
import com.ailms.common.dto.ChatRequest;
import com.ailms.common.dto.ChatResponse;
import com.ailms.common.dto.RetrievedChunk;
import com.ailms.orchestrator.agent.ContentAnalysisAgent;
import com.ailms.orchestrator.agent.ConversationAgent;
import com.ailms.orchestrator.agent.InsightAgent;
import com.ailms.orchestrator.agent.IntentClassifier;
import com.ailms.orchestrator.agent.ProfilingAgent;
import com.ailms.orchestrator.agent.QuestionGenerationAgent;
import com.ailms.orchestrator.agent.ResponseComposer;
import com.ailms.orchestrator.agent.ResponseVerifierAgent;
import com.ailms.orchestrator.repository.ConversationRepository;
import com.ailms.orchestrator.repository.QuizResultRepository;
import com.ailms.orchestrator.repository.UserProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.List;
import java.util.function.Predicate;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

  @Mock QuizResultRepository quizResultRepository;

  @Mock UserProfileRepository userProfileRepository;
  @Mock VectorDBService vectorDBService;
  @Mock ContentDocumentService contentDocumentService;
  @Mock InsightDataService insightDataService;
  @Mock KafkaEventPublisher kafkaEventPublisher;
  @Mock YouTubeLinkValidator youTubeLinkValidator;
  @Mock YouTubeSearchService youTubeSearchService;
  @Mock RedisChatMemoryStore chatMemoryStore;
  @Mock ManagedExecutor executor;
  @Mock AsyncJobRunner asyncJobRunner;

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
  void routeAsync_returnsPendingAndSchedulesJob() {
    OrchestratorService svc = buildService();

    java.util.Map<String, Object> ack =
        svc.routeAsync(
            new ChatRequest(
                "Analyze the uploaded file: doc-9", "upload:doc-9"),
            "user-1");

    assertEquals("PENDING", ack.get("status"));
    assertEquals("upload:doc-9", ack.get("sessionId"));
    verify(executor).execute(any(Runnable.class));
    verify(conversationAgent, never()).process(anyString(), anyString());
  }

  @Test
  void routeAsync_rejectsSessionOwnedByAnotherUser() {
    when(conversationRepository.sessionOwner("upload:doc-9")).thenReturn("user-other");
    OrchestratorService svc = buildService();

    assertThrows(
        SessionOwnershipException.class,
        () ->
            svc.routeAsync(
                new ChatRequest("Analyze the uploaded file: doc-9", "upload:doc-9"), "user-1"));
    verify(executor, never()).execute(any(Runnable.class));
  }

  @Test
  void routeAsync_jobFailureMarksDocumentFailedAndEmitsFallback() {
    doThrow(new RuntimeException("embedding service down"))
        .when(profilingService)
        .ensureProfile(anyString());
    doAnswer(
            inv -> {
              inv.getArgument(0, Runnable.class).run();
              return null;
            })
        .when(asyncJobRunner)
        .run(any(Runnable.class));
    OrchestratorService svc = buildService();

    svc.routeAsync(
        new ChatRequest("Analyze the uploaded file: doc-9", "upload:doc-9"), "user-1");

    ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
    verify(executor).execute(captor.capture());
    captor.getValue().run();

    verify(contentDocumentService).markFailed(eq("doc-9"), contains("embedding service down"));
    verify(kafkaEventPublisher)
        .publishContentAnalysisComplete(
            eq("user-1"),
            eq("upload:doc-9"),
            contains("couldn't finish analyzing"));
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
    verify(responseVerifierAgent, never()).verify(anyString(), anyString(), anyString());
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
        .thenReturn(chunks("context from qdrant"));
    when(questionGenerationAgent.process(
            eq(ChatMemoryKeys.assessment("sess-1")), anyString(), contains("context from qdrant"), eq("medium"), eq(5)))
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
        .thenReturn(chunks("rome content"));
    when(questionGenerationAgent.process(
            eq(ChatMemoryKeys.assessment("sess-1")), anyString(), contains("rome content"), eq("medium"), eq(5)))
        .thenReturn("Rome assessment");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenReturn(new ChatResponse("Rome assessment", "sess-1", "ASSESSMENT"));

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest(msg, "sess-1"), "user-1");

    assertEquals("Rome assessment", resp.message());
    verify(vectorDBService, times(2)).retrieveRelevantContext(anyString(), eq(3), eq("doc:doc-9"));
    verify(questionGenerationAgent)
        .process(eq(ChatMemoryKeys.assessment("sess-1")), anyString(), anyString(), eq("medium"), eq(5));
  }

  @Test
  void route_assessment_includesCAAAnalysisFromMemory() {
    when(conversationRepository.lastUploadedDocumentId("user-1", "sess-1")).thenReturn("doc-9");
    when(intentClassifier.classify("quiz me")).thenReturn("ASSESSMENT");
    when(vectorDBService.retrieveRelevantContext(anyString(), eq(3), eq("doc:doc-9")))
        .thenReturn(chunks("chunk from vector db"));
    ChatMessage caaAnalysis = AiMessage.from("Topics: Photosynthesis\nKey concepts: chlorophyll");
    when(chatMemoryStore.getMessages(ChatMemoryKeys.analysis("sess-1")))
        .thenReturn(java.util.List.of(caaAnalysis));
    when(questionGenerationAgent.process(
            eq(ChatMemoryKeys.assessment("sess-1")),
            anyString(),
            contains("Topics: Photosynthesis"),
            eq("medium"),
            eq(5)))
        .thenReturn("Assessment from analysis");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenReturn(new ChatResponse("Assessment from analysis", "sess-1", "ASSESSMENT"));

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest("quiz me", "sess-1"), "user-1");

    assertEquals("Assessment from analysis", resp.message());
    verify(questionGenerationAgent)
        .process(
            eq(ChatMemoryKeys.assessment("sess-1")),
            anyString(),
            contains("Topics: Photosynthesis"),
            eq("medium"),
            eq(5));
  }

  @Test
  void route_assessment_parsesDifficultyAndCountFromFreeText() {
    String msg = "Generate 10 hard questions about the document";
    when(intentClassifier.classify(msg)).thenReturn("ASSESSMENT");
    when(conversationRepository.lastUploadedDocumentId("user-1", "sess-1")).thenReturn("doc-9");
    when(vectorDBService.retrieveRelevantContext(anyString(), eq(3), eq("doc:doc-9")))
        .thenReturn(chunks("context from qdrant"));
    when(questionGenerationAgent.process(
            eq(ChatMemoryKeys.assessment("sess-1")),
            anyString(),
            anyString(),
            eq("hard"),
            eq(10)))
        .thenReturn("Hard quiz");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenReturn(new ChatResponse("Hard quiz", "sess-1", "ASSESSMENT"));

    OrchestratorService svc = buildService();
    svc.route(new ChatRequest(msg, "sess-1"), "user-1");

    verify(questionGenerationAgent)
        .process(eq(ChatMemoryKeys.assessment("sess-1")), anyString(), anyString(), eq("hard"), eq(10));
  }

  @Test
  void route_assessment_defaultsDifficultyAndCount() {
    when(intentClassifier.classify("quiz me")).thenReturn("ASSESSMENT");
    when(conversationRepository.lastUploadedDocumentId("user-1", "sess-1")).thenReturn("doc-9");
    when(vectorDBService.retrieveRelevantContext(anyString(), eq(3), eq("doc:doc-9")))
        .thenReturn(chunks("context from qdrant"));
    when(questionGenerationAgent.process(
            eq(ChatMemoryKeys.assessment("sess-1")),
            anyString(),
            anyString(),
            eq("medium"),
            eq(5)))
        .thenReturn("Default quiz");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenReturn(new ChatResponse("Default quiz", "sess-1", "ASSESSMENT"));

    OrchestratorService svc = buildService();
    svc.route(new ChatRequest("quiz me", "sess-1"), "user-1");

    verify(questionGenerationAgent)
        .process(
            eq(ChatMemoryKeys.assessment("sess-1")),
            anyString(),
            anyString(),
            eq("medium"),
            eq(5));
  }

  @Test
  void route_assessment_parsesStructuredParamSuffix() {
    String msg = "Generate assessment for content doc-9 | questions=3 | difficulty=easy";
    when(intentClassifier.classify(msg)).thenReturn("ASSESSMENT");
    when(vectorDBService.retrieveRelevantContext(anyString(), eq(3), eq("doc:doc-9")))
        .thenReturn(chunks("rome content"));
    when(questionGenerationAgent.process(
            eq(ChatMemoryKeys.assessment("sess-1")),
            anyString(),
            anyString(),
            eq("easy"),
            eq(3)))
        .thenReturn("Easy quiz");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenReturn(new ChatResponse("Easy quiz", "sess-1", "ASSESSMENT"));

    OrchestratorService svc = buildService();
    svc.route(new ChatRequest(msg, "sess-1"), "user-1");

    verify(questionGenerationAgent)
        .process(
            eq(ChatMemoryKeys.assessment("sess-1")),
            anyString(),
            anyString(),
            eq("easy"),
            eq(3));
  }

  @Test
  void route_assessment_fallsBackToCAAFromUploadSession() {
    when(intentClassifier.classify("quiz me")).thenReturn("ASSESSMENT");
    when(conversationRepository.lastUploadedDocumentId("user-1", "sess-1")).thenReturn("doc-9");
    when(chatMemoryStore.getMessages(ChatMemoryKeys.analysis("sess-1")))
        .thenReturn(java.util.List.of());
    ChatMessage uploadCaa = AiMessage.from("Analysis from upload session");
    when(chatMemoryStore.getMessages(ChatMemoryKeys.analysis("upload:doc-9")))
        .thenReturn(java.util.List.of(uploadCaa));
    when(vectorDBService.retrieveRelevantContext(anyString(), eq(3), eq("doc:doc-9")))
        .thenReturn(chunks("chunk from qdrant"));
    when(questionGenerationAgent.process(
            eq(ChatMemoryKeys.assessment("sess-1")),
            anyString(),
            contains("Analysis from upload session"),
            eq("medium"),
            eq(5)))
        .thenReturn("Grounded quiz");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenReturn(new ChatResponse("Grounded quiz", "sess-1", "ASSESSMENT"));

    OrchestratorService svc = buildService();
    svc.route(new ChatRequest("quiz me", "sess-1"), "user-1");

    verify(questionGenerationAgent)
        .process(
            eq(ChatMemoryKeys.assessment("sess-1")),
            anyString(),
            contains("Analysis from upload session"),
            eq("medium"),
            eq(5));
  }

  @Test
  void route_assessment_parsesStructuredJsonOutputIntoMetadata() {
    String json =
        "[{\"question\":\"What is 2+2?\",\"type\":\"multiple_choice\","
            + "\"options\":[\"3\",\"4\",\"5\"],\"answer\":\"4\","
            + "\"explanation\":\"Basic addition\"}]";
    when(intentClassifier.classify("quiz me")).thenReturn("ASSESSMENT");
    when(conversationRepository.lastUploadedDocumentId("user-1", "sess-1")).thenReturn("doc-9");
    when(vectorDBService.retrieveRelevantContext(anyString(), eq(3), eq("doc:doc-9")))
        .thenReturn(chunks("context from qdrant"));
    when(questionGenerationAgent.process(
            eq(ChatMemoryKeys.assessment("sess-1")),
            anyString(),
            anyString(),
            eq("medium"),
            eq(5)))
        .thenReturn(json);
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenAnswer(
            inv -> {
              AgenticScope scope = inv.getArgument(0);
              com.ailms.common.dto.QuizMetadata meta =
                  scope.readState("quizMetadata", null);
              return new ChatResponse("Assessment result", "sess-1", "ASSESSMENT", meta);
            });

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest("quiz me", "sess-1"), "user-1");

    assertInstanceOf(com.ailms.common.dto.QuizMetadata.class, resp.metadata());
    var meta = (com.ailms.common.dto.QuizMetadata) resp.metadata();
    assertEquals(1, meta.items().size());
    assertEquals("What is 2+2?", meta.items().get(0).question());
    assertEquals("4", meta.items().get(0).answer());
    assertEquals(5, meta.questionCount());
    assertEquals("medium", meta.difficulty());
  }

  @Test
  void route_assessment_unparseableOutput_hasNoMetadata() {
    when(intentClassifier.classify("quiz me")).thenReturn("ASSESSMENT");
    when(conversationRepository.lastUploadedDocumentId("user-1", "sess-1")).thenReturn("doc-9");
    when(vectorDBService.retrieveRelevantContext(anyString(), eq(3), eq("doc:doc-9")))
        .thenReturn(chunks("context from qdrant"));
    when(questionGenerationAgent.process(
            eq(ChatMemoryKeys.assessment("sess-1")),
            anyString(),
            anyString(),
            eq("medium"),
            eq(5)))
        .thenReturn("1. What is X?\n   Answer: Y");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenAnswer(
            inv -> {
              AgenticScope scope = inv.getArgument(0);
              Object meta = scope.readState("quizMetadata", null);
              return new ChatResponse("Assessment result", "sess-1", "ASSESSMENT", meta);
            });

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest("quiz me", "sess-1"), "user-1");

    assertNull(resp.metadata());
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
  void route_verifier_nullVerdict_failsClosedReturnsFallback() {
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

    assertEquals(
        "I'm sorry, I couldn't generate a good answer. Could you rephrase?", resp.message());
    verify(conversationAgent, times(2)).process(eq("conversation:sess-1"), anyString());
    verify(responseVerifierAgent, times(2))
        .verify(eq("what is a neural network"), anyString(), anyString());
  }

  @Test
  void route_verifier_malformedJson_failsClosedReturnsFallback() {
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

    assertEquals(
        "I'm sorry, I couldn't generate a good answer. Could you rephrase?", resp.message());
    verify(conversationAgent, times(2)).process(eq("conversation:sess-1"), anyString());
  }

  @Test
  void route_verifier_throws_failsClosedReturnsFallback() {
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

    assertEquals(
        "I'm sorry, I couldn't generate a good answer. Could you rephrase?", resp.message());
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
        .thenReturn(chunks("rome context"));
    when(questionGenerationAgent.process(
            eq(ChatMemoryKeys.assessment("sess-1")), anyString(), contains("rome context"), eq("medium"), eq(5)))
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
        .process(eq(ChatMemoryKeys.assessment("sess-1")), anyString(), anyString(), eq("medium"), eq(5));
  }

  @Test
  void route_docReference_question_fallsBackToUserWideDocument() {
    String msg = "Questions based on the document";
    when(intentClassifier.classify(msg)).thenReturn("ASSESSMENT");
    when(contentDocumentService.resolveRecentDocumentId("user-1", "sess-1")).thenReturn(null);
    when(conversationRepository.lastUploadedDocumentId("user-1", "sess-1")).thenReturn(null);
    when(contentDocumentService.resolveRecentDocumentId("user-1", null)).thenReturn("doc-9");
    when(vectorDBService.retrieveRelevantContext(anyString(), eq(3), eq("doc:doc-9")))
        .thenReturn(chunks("rome context"));
    when(questionGenerationAgent.process(
            eq(ChatMemoryKeys.assessment("sess-1")), anyString(), contains("rome context"), eq("medium"), eq(5)))
        .thenReturn("Rome questions");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenReturn(new ChatResponse("Rome questions", "sess-1", "ASSESSMENT"));

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest(msg, "sess-1"), "user-1");

    assertEquals("ASSESSMENT", resp.agentType());
    verify(contentDocumentService, atLeastOnce()).resolveRecentDocumentId("user-1", null);
    verify(questionGenerationAgent)
        .process(eq(ChatMemoryKeys.assessment("sess-1")), anyString(), anyString(), eq("medium"), eq(5));
  }

  @Test
  void route_docReference_summarize_routesToContentAnalysis() {
    String msg = "Summarize the uploaded document";
    when(intentClassifier.classify(msg)).thenReturn("CONTENT_ANALYSIS");
    when(contentDocumentService.resolveRecentDocumentId("user-1", "sess-1")).thenReturn("doc-9");
    when(vectorDBService.retrieveRelevantContext(anyString(), eq(8), eq("doc:doc-9")))
        .thenReturn(chunks("rome context"));
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
    verify(questionGenerationAgent, never()).process(anyString(), anyString(), anyString(), anyString(), anyInt());
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

  @Test
  void parseDifficulty_handlesWordAndParamForms() {
    assertEquals("hard", OrchestratorService.parseDifficulty("give me hard questions"));
    assertEquals("easy", OrchestratorService.parseDifficulty("easy quiz please"));
    assertEquals(
        "easy",
        OrchestratorService.parseDifficulty(
            "Generate assessment for content doc-1 | questions=5 | difficulty=easy"));
    assertEquals("medium", OrchestratorService.parseDifficulty("quiz me"));
    assertEquals("medium", OrchestratorService.parseDifficulty(null));
  }

  @Test
  void parseQuestionCount_handlesWordAndParamForms() {
    assertEquals(10, OrchestratorService.parseQuestionCount("Generate 10 questions"));
    assertEquals(3, OrchestratorService.parseQuestionCount("ask me 3 hard questions"));
    assertEquals(
        8, OrchestratorService.parseQuestionCount("Generate assessment for content doc-1 | questions=8"));
    assertEquals(5, OrchestratorService.parseQuestionCount("quiz me"));
    assertEquals(5, OrchestratorService.parseQuestionCount(null));
    assertEquals(50, OrchestratorService.parseQuestionCount("200 questions"));
    assertEquals(1, OrchestratorService.parseQuestionCount("0 questions"));
  }

  @Test
  void route_verifier_doubleRejection_returnsFallback() {
    when(intentClassifier.classify("what is a neural network")).thenReturn("CONVERSATION");
    when(conversationAgent.process(eq("conversation:sess-1"), anyString()))
        .thenReturn("Bad answer first.")
        .thenReturn("Bad answer second.");
    when(responseVerifierAgent.verify(
            eq("what is a neural network"), anyString(), eq("Bad answer first.")))
        .thenReturn("{\"verdict\": \"NEEDS_REWRITE\", \"reason\": \"off-topic\"}");
    when(responseVerifierAgent.verify(
            eq("what is a neural network"), anyString(), eq("Bad answer second.")))
        .thenReturn("{\"verdict\": \"NEEDS_REWRITE\", \"reason\": \"still bad\"}");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenAnswer(
            inv -> {
              AgenticScope scope = inv.getArgument(0);
              String msg = scope.readState("response", "");
              return new ChatResponse(msg, "sess-1", "CONVERSATION");
            });

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest("what is a neural network", "sess-1"), "user-1");

    assertEquals(
        "I'm sorry, I couldn't generate a good answer. Could you rephrase?", resp.message());
    verify(conversationAgent, times(2)).process(eq("conversation:sess-1"), anyString());
    verify(responseVerifierAgent, times(2))
        .verify(eq("what is a neural network"), anyString(), anyString());
  }

  @Test
  void route_verifier_retryBlank_returnsFallback() {
    when(intentClassifier.classify("what is a neural network")).thenReturn("CONVERSATION");
    when(conversationAgent.process(eq("conversation:sess-1"), anyString()))
        .thenReturn("Rejected answer.")
        .thenReturn("");
    when(responseVerifierAgent.verify(
            eq("what is a neural network"), anyString(), eq("Rejected answer.")))
        .thenReturn("{\"verdict\": \"NEEDS_REWRITE\", \"reason\": \"bad\"}");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenAnswer(
            inv -> {
              AgenticScope scope = inv.getArgument(0);
              String msg = scope.readState("response", "");
              return new ChatResponse(msg, "sess-1", "CONVERSATION");
            });

    OrchestratorService svc = buildService();
    ChatResponse resp = svc.route(new ChatRequest("what is a neural network", "sess-1"), "user-1");

    assertEquals(
        "I'm sorry, I couldn't generate a good answer. Could you rephrase?", resp.message());
    verify(conversationAgent, times(2)).process(eq("conversation:sess-1"), anyString());
  }

  @Test
  void route_profilingAgent_receivesRawMessage_notEnriched() {
    when(intentClassifier.classify("quiz me")).thenReturn("ASSESSMENT");
    when(contentDocumentService.resolveRecentDocumentId("user-1", "sess-1")).thenReturn("doc-9");
    when(vectorDBService.retrieveRelevantContext(anyString(), eq(3), eq("doc:doc-9")))
        .thenReturn(chunks("some context from vector db"));
    when(questionGenerationAgent.process(
            eq(ChatMemoryKeys.assessment("sess-1")), anyString(), anyString(), eq("medium"), eq(5)))
        .thenReturn("Assessment result");
    when(responseComposer.compose(any(AgenticScope.class), eq("sess-1")))
        .thenReturn(new ChatResponse("Assessment result", "sess-1", "ASSESSMENT"));

    OrchestratorService svc = buildService();
    svc.route(new ChatRequest("quiz me", "sess-1"), "user-1");

    verify(profilingAgent)
        .process(eq(ChatMemoryKeys.profiling("sess-1")), eq("quiz me"));
  }

  private static List<RetrievedChunk> chunks(String... texts) {
    return java.util.Arrays.stream(texts)
        .map(t -> new RetrievedChunk(t, "doc:doc-9", 0.7, 0))
        .toList();
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
    svc.quizResultRepository = quizResultRepository;
    svc.userProfileRepository = userProfileRepository;
    svc.vectorDBService = vectorDBService;
    svc.contentDocumentService = contentDocumentService;
    svc.insightDataService = insightDataService;
    svc.kafkaEventPublisher = kafkaEventPublisher;
    svc.youTubeLinkValidator = youTubeLinkValidator;
    svc.youTubeSearchService = youTubeSearchService;
    svc.chatMemoryStore = chatMemoryStore;
    svc.executor = executor;
    svc.asyncJobRunner = asyncJobRunner;
    svc.objectMapper = new ObjectMapper();
    lenient()
        .when(youTubeLinkValidator.sanitize(anyString()))
        .thenAnswer(inv -> inv.getArgument(0));
    return svc;
  }

  @Test
  void recordQuizResult_persistsAndSkipsProfilingOnGoodScore() {
    var item =
        new com.ailms.common.dto.QuizItem(
            "Q1", "multiple_choice", java.util.List.of("A", "B"), "B", "why B");
    var request =
        new com.ailms.common.dto.QuizResultRequest(
            "sess-1", "doc-9", java.util.List.of(item), java.util.Map.of("Q1", "B"), 1, 1);

    OrchestratorService svc = buildService();
    svc.recordQuizResult("user-1", request);

    verify(quizResultRepository).save(argThat(r -> r.userId.equals("user-1")));
    verify(userProfileRepository, never()).findOrCreate(anyString());
  }

  @Test
  void recordQuizResult_appendsWeakAreaNoteWhenBelowHalf() {
    var item =
        new com.ailms.common.dto.QuizItem(
            "Q1", "multiple_choice", java.util.List.of("A", "B"), "B", "why B");
    var request =
        new com.ailms.common.dto.QuizResultRequest(
            "sess-1", "doc-9", java.util.List.of(item), java.util.Map.of("Q1", "A"), 0, 1);
    var profile = new com.ailms.common.entity.UserProfile();
    when(userProfileRepository.findOrCreate("user-1")).thenReturn(profile);

    OrchestratorService svc = buildService();
    svc.recordQuizResult("user-1", request);

    assertTrue(profile.behavioralTraits.contains("Quiz 0/1"));
    assertTrue(profile.behavioralTraits.contains("Q1"));
  }

  @Test
  void recordQuizResult_rejectsEmptyPayload() {
    OrchestratorService svc = buildService();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            svc.recordQuizResult(
                "user-1",
                new com.ailms.common.dto.QuizResultRequest(
                    "sess-1", "doc-9", java.util.List.of(), java.util.Map.of(), 0, 0)));
  }
}
