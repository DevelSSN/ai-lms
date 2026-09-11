package com.ailms.orchestrator.service;

import com.ailms.common.constants.ChatMemoryKeys;
import com.ailms.common.constants.PromptPrefixes;
import com.ailms.common.constants.VectorSourceKeys;
import com.ailms.common.dto.ChatHistory;
import com.ailms.common.dto.ChatRequest;
import com.ailms.common.dto.ChatResponse;
import com.ailms.common.dto.QuizItem;
import com.ailms.common.dto.QuizMetadata;
import com.ailms.common.dto.QuizResultRequest;
import com.ailms.common.dto.RetrievedChunk;
import com.ailms.common.entity.ConversationLog;
import com.ailms.common.entity.QuizResult;
import com.ailms.common.entity.UserProfile;
import com.ailms.common.enums.ChatRole;
import com.ailms.common.enums.IntentType;
import com.ailms.orchestrator.agent.ContentAnalysisAgent;
import com.ailms.orchestrator.agent.ConversationAgent;
import com.ailms.orchestrator.agent.InsightAgent;
import com.ailms.orchestrator.agent.IntentClassifier;
import com.ailms.orchestrator.agent.ProfilingAgent;
import com.ailms.orchestrator.agent.QuestionGenerationAgent;
import com.ailms.orchestrator.agent.ResponseComposer;
import com.ailms.orchestrator.agent.ResponseVerifierAgent;
import com.ailms.orchestrator.agent.TitleGenerator;
import com.ailms.orchestrator.repository.ConversationRepository;
import com.ailms.orchestrator.repository.QuizResultRepository;
import com.ailms.orchestrator.repository.UserProfileRepository;
import com.ailms.orchestrator.util.TextUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.LangChain4jManaged;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class OrchestratorService {

  @Inject IntentClassifier intentClassifier;

  @Inject ProfilingAgent profilingAgent;

  @Inject ResponseComposer responseComposer;

  @Inject ConversationAgent conversationAgent;

  @Inject ContentAnalysisAgent contentAnalysisAgent;

  @Inject QuestionGenerationAgent questionGenerationAgent;

  @Inject InsightAgent insightAgent;

  @Inject ProfilingService profilingService;

  @Inject ConversationRepository conversationRepository;

  @Inject QuizResultRepository quizResultRepository;

  @Inject UserProfileRepository userProfileRepository;

  @Inject VectorDBService vectorDBService;

  @Inject ContentDocumentService contentDocumentService;

  @Inject InsightDataService insightDataService;

  @Inject YouTubeLinkValidator youTubeLinkValidator;

  @Inject YouTubeSearchService youTubeSearchService;

  @Inject TitleGenerator titleGenerator;

  @Inject ResponseVerifierAgent responseVerifierAgent;

  ExecutorService executor = Executors.newFixedThreadPool(2);

  @Inject RedisChatMemoryStore chatMemoryStore;

  @Inject ObjectMapper objectMapper;

  private static final Pattern EXPLICIT_VIDEO_LINK =
      Pattern.compile("(?i)\\b(?:https?://|www\\.)?(?:m\\.)?(?:youtube\\.com|youtu\\.be)/");

  private static final String NO_VIDEOS_BARE =
      "I couldn't find any YouTube videos for that request. "
          + "Try asking like \"youtube videos about <topic>\".";

  private static final String NO_VIDEOS_FOR = "I couldn't find any YouTube videos for '";

  private static final Set<String> KNOWN_INTENTS =
      java.util.Arrays.stream(IntentType.values())
          .map(IntentType::name)
          .collect(java.util.stream.Collectors.toSet());

  private static final String INTENT_CONVERSATION = IntentType.CONVERSATION.name();
  private static final String INTENT_VIDEO_SEARCH = IntentType.VIDEO_SEARCH.name();
  private static final String INTENT_CONTENT_ANALYSIS = IntentType.CONTENT_ANALYSIS.name();
  private static final String INTENT_ASSESSMENT = IntentType.ASSESSMENT.name();
  private static final String INTENT_INSIGHT = IntentType.INSIGHT.name();

  private static final String VERIFIER_FALLBACK =
      "I'm sorry, I couldn't generate a good answer. Could you rephrase?";

  private static final String UPLOAD_PREFIX = PromptPrefixes.UPLOAD_ANALYSIS;

  private static final String ASSESS_PREFIX = "Generate assessment for content ";

  private static final String ASSESS_PARAMS_MARKER = " | ";

  private static final String DEFAULT_DIFFICULTY = "medium";

  private static final int DEFAULT_QUESTION_COUNT = 5;

  private static final int MAX_QUESTION_COUNT = 50;

  private static final Pattern DIFFICULTY_PARAM =
      Pattern.compile("(?i)difficulty=(easy|medium|hard)");

  private static final Pattern DIFFICULTY_WORD = Pattern.compile("(?i)\\b(easy|medium|hard)\\b");

  private static final Pattern COUNT_PARAM = Pattern.compile("(?i)questions=(\\d{1,3})");

  private static final Pattern COUNT_WORD =
      Pattern.compile(
          "(?i)\\b(\\d{1,3})\\s+(?:(?:easy|medium|hard|tough|difficult|simple|quiz|practice|mcq)\\s+)*questions?\\b");

  /**
   * Extracts the requested difficulty (easy|medium|hard) from the user message, preferring an
   * explicit structured {@code difficulty=X} parameter, then a standalone word. Defaults to
   * {@code medium}.
   */
  static String parseDifficulty(String message) {
    if (message == null) return DEFAULT_DIFFICULTY;
    var param = DIFFICULTY_PARAM.matcher(message);
    if (param.find()) return param.group(1).toLowerCase(Locale.ROOT);
    var word = DIFFICULTY_WORD.matcher(message);
    if (word.find()) return word.group(1).toLowerCase(Locale.ROOT);
    return DEFAULT_DIFFICULTY;
  }

  /**
   * Extracts the requested question count from the user message, preferring an explicit
   * structured {@code questions=N} parameter, then a count word ("10 questions"). Defaults to
   * {@code 5} and clamps to [1, 50].
   */
  static int parseQuestionCount(String message) {
    if (message == null) return DEFAULT_QUESTION_COUNT;
    var param = COUNT_PARAM.matcher(message);
    if (param.find()) return clampQuestionCount(Integer.parseInt(param.group(1)));
    var word = COUNT_WORD.matcher(message);
    if (word.find()) return clampQuestionCount(Integer.parseInt(word.group(1)));
    return DEFAULT_QUESTION_COUNT;
  }

  private static int clampQuestionCount(int count) {
    return Math.max(1, Math.min(count, MAX_QUESTION_COUNT));
  }

  /**
   * Extracts the content id from an assessment prompt such as "Generate assessment for content
   * doc-1 | questions=5 | difficulty=medium", ignoring any trailing question-count/difficulty
   * parameters appended by the gateway.
   */
  static String assessmentTargetId(String message) {
    String tail = message.substring(ASSESS_PREFIX.length()).trim();
    int marker = tail.indexOf(ASSESS_PARAMS_MARKER);
    if (marker >= 0) {
      tail = tail.substring(0, marker).trim();
    }
    return tail;
  }

  private static final int CHUNK_SIZE = 800;

  private static final int CHUNK_OVERLAP = 100;

  private static final int ANALYSIS_TOP_K = 8;

  @Inject KafkaEventPublisher kafkaEventPublisher;

  @Inject AsyncJobRunner asyncJobRunner;

  public ChatResponse route(ChatRequest request, String userId) {
    profilingService.ensureProfile(userId);

    String sessionId = request.sessionId();
    if (sessionId == null || sessionId.isBlank()) {
      sessionId = java.util.UUID.randomUUID().toString();
      log.info("Generated session id for user={}: {}", userId, sessionId);
    }
    enforceSessionOwnership(sessionId, userId);

    AgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
    LangChain4jManaged.setCurrent(Map.of(AgenticScope.class, scope));
    try {
      String message = request.message();
      String greetingResponse =
          TextUtils.isBareGreeting(message)
              ? "Hello! I'm your AI tutor. What would you like to learn today?"
              : null;

      String intent;
      String enrichedMessage;
      String agentResponse = null;
      if (greetingResponse != null) {
        intent = INTENT_CONVERSATION;
        enrichedMessage = message;
        agentResponse = greetingResponse;
        log.info(
            "Intent={} (greeting short-circuit) for user={} message={}",
            INTENT_CONVERSATION,
            userId,
            message);
      } else if (isExplicitVideoLink(message)) {
        intent = INTENT_VIDEO_SEARCH;
        enrichedMessage = message;
        log.info(
            "Intent={} (explicit video link short-circuit) for user={} message={}",
            INTENT_VIDEO_SEARCH,
            userId,
            message);
      } else {
        intent = normalizeIntent(intentClassifier.classify(message));
        intent = reclassifyContentIntent(intent, message, sessionId, userId);
        log.info("Intent={} for user={} message={}", intent, userId, message);
        enrichedMessage = enrichWithContext(intent, message, sessionId, userId, scope);
      }

      if (INTENT_INSIGHT.equals(intent)) {
        enrichedMessage = enrichWithAnalytics(userId, sessionId, message, enrichedMessage);
      }

      scope.writeState("intent", intent);

      String analysisCtx = "";
      if (agentResponse == null) {
        analysisCtx = resolveAnalysisContext(intent, message, sessionId, userId);
        if (INTENT_VIDEO_SEARCH.equals(intent)) {
          agentResponse = tryVideoSearch(message, sessionId, userId);
        }
        if (agentResponse == null) {
          agentResponse = dispatchAgent(intent, sessionId, enrichedMessage, analysisCtx);
        }
      }
      agentResponse = youTubeLinkValidator.sanitize(agentResponse);
      agentResponse = TextUtils.stripThinking(agentResponse);

      if (greetingResponse == null) {
        agentResponse =
            verifyAndRetry(
                intent, sessionId, message, enrichedMessage, analysisCtx, agentResponse, userId);
      }

      if (agentResponse == null || agentResponse.isBlank()) {
        log.warn("Router returned blank response for intent={} user={}", intent, userId);
      }

      if (greetingResponse == null) {
        try {
          String profileUpdate =
              profilingAgent.process(ChatMemoryKeys.profiling(sessionId), message);
          if (profileUpdate != null && !profileUpdate.isBlank()) {
            profilingService.applyProfileUpdate(userId, profileUpdate);
            kafkaEventPublisher.publishProfileUpdated(userId, sessionId, profileUpdate);
          }
        } catch (Exception e) {
          log.warn(
              "Profiling pipeline failed for user={} session={}: {}",
              userId,
              sessionId,
              e.getMessage());
        }
      }

      scope.writeState("response", agentResponse);
      writeAgentScopeKey(intent, agentResponse, scope);
      attachQuizMetadata(intent, sessionId, message, agentResponse, scope, userId);
      ChatResponse response = responseComposer.compose(scope, sessionId);

      boolean isNewSession =
          conversationRepository.count(
                  "sessionId = ?1 AND (deleted IS NULL OR deleted = false)", sessionId)
              == 0;
      conversationRepository.logMessage(userId, sessionId, ChatRole.USER.key(), request.message());
      conversationRepository.logMessage(
          userId, sessionId, ChatRole.ASSISTANT.key(), response.message(), response.agentType());

      if (isNewSession) scheduleTitleGeneration(userId, sessionId);

      publishAgentEvent(intent, userId, sessionId, response.message());

      log.info("Response ready for user={} type={}", userId, intent);
      return response;
    } finally {
      LangChain4jManaged.removeCurrent();
    }
  }

  /**
   * Schedules a content-analysis run on a background worker and returns immediately. The upload
   * pipeline (S3 read -> chunk -> embed -> ContentAnalysisAgent) is executed asynchronously; the
   * result is delivered to the chat UI through the existing content-analysis-complete Kafka event.
   */
  public Map<String, Object> routeAsync(ChatRequest request, String userId) {
    String sessionId = request.sessionId();
    if (sessionId == null || sessionId.isBlank()) {
      sessionId = java.util.UUID.randomUUID().toString();
      log.info("Generated session id for async analysis user={}: {}", userId, sessionId);
    }
    enforceSessionOwnership(sessionId, userId);

    ChatRequest job = new ChatRequest(request.message(), sessionId);
    String finalSessionId = sessionId;
    executor.execute(() -> asyncJobRunner.run(() -> runAnalysisJob(job, userId, finalSessionId)));

    log.info("Scheduled async content analysis for user={} session={}", userId, finalSessionId);
    return Map.of("status", "PENDING", "sessionId", finalSessionId);
  }

  private void runAnalysisJob(ChatRequest request, String userId, String sessionId) {
    String docId = null;
    String raw = request.message();
    if (raw != null && raw.startsWith(UPLOAD_PREFIX)) {
      docId = raw.substring(UPLOAD_PREFIX.length()).trim();
    }
    try {
      route(request, userId);
    } catch (Exception e) {
      log.error(
          "Async content analysis failed for user={} session={}: {}",
          userId,
          sessionId,
          e.getMessage(),
          e);
      String error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
      if (docId != null && !docId.isEmpty()) {
        try {
          contentDocumentService.markFailed(docId, error);
        } catch (Exception markErr) {
          log.warn(
              "Failed to mark doc {} FAILED: {}",
              docId,
              markErr.getMessage());
        }
      }
      emitFallbackAnalysis(userId, sessionId, error);
    }
  }

  private void emitFallbackAnalysis(String userId, String sessionId, String error) {
    try {
      kafkaEventPublisher.publishContentAnalysisComplete(
          userId, sessionId, "I couldn't finish analyzing your document: " + error);
    } catch (Exception e) {
      log.warn("Failed to publish fallback analysis event for session={}: {}", sessionId, e.getMessage());
    }
  }

  void enforceSessionOwnership(String sessionId, String userId) {
    String owner = conversationRepository.sessionOwner(sessionId);
    if (owner != null && !owner.equals(userId)) {
      log.warn(
          "Denied cross-user session access: session={} owned by={} requested by={}",
          sessionId,
          owner,
          userId);
      throw new SessionOwnershipException(
          "Session " + sessionId + " does not belong to user " + userId);
    }
  }

  private String normalizeIntent(String raw) {
    if (raw == null) return INTENT_CONVERSATION;
    String normalized = raw.trim().toUpperCase(Locale.ROOT).replaceFirst("\\.$", "");
    if (!KNOWN_INTENTS.contains(normalized)) {
      log.warn("Unrecognized classifier output '{}', defaulting to {}", raw, INTENT_CONVERSATION);
      return INTENT_CONVERSATION;
    }
    return normalized;
  }

  private String reclassifyContentIntent(
      String intent, String message, String sessionId, String userId) {
    if (!IntentType.isAnalysis(intent)) return intent;
    if (message.startsWith(UPLOAD_PREFIX) || message.startsWith(ASSESS_PREFIX)) return intent;
    String activeDocId = null;
    try {
      activeDocId = resolveActiveDocumentId(message, sessionId, userId);
    } catch (Exception e) {
      log.warn(
          "Failed to resolve active document for intent={} session={}: {}",
          intent,
          sessionId,
          e.getMessage());
    }
    if (activeDocId == null) {
      log.info(
          "No session document for intent={}, reclassifying to {}", intent, INTENT_CONVERSATION);
      return INTENT_CONVERSATION;
    }
    return intent;
  }

  private String dispatchAgent(
      String intent, String sessionId, String message, String analysisCtx) {
    return switch (IntentType.fromName(intent)) {
      case CONTENT_ANALYSIS ->
          contentAnalysisAgent.process(ChatMemoryKeys.analysis(sessionId), message);
      case ASSESSMENT ->
          questionGenerationAgent.process(
              ChatMemoryKeys.assessment(sessionId),
              message,
              analysisCtx,
              parseDifficulty(message),
              parseQuestionCount(message));
      case INSIGHT -> insightAgent.process(ChatMemoryKeys.insight(sessionId), message);
      default -> conversationAgent.process(ChatMemoryKeys.conversation(sessionId), message);
    };
  }

  private void publishAgentEvent(String intent, String userId, String sessionId, String message) {
    try {
      switch (IntentType.fromName(intent)) {
        case CONTENT_ANALYSIS ->
            kafkaEventPublisher.publishContentAnalysisComplete(userId, sessionId, message);
        case INSIGHT -> kafkaEventPublisher.publishInsightGenerated(userId, sessionId, message);
        default -> {}
      }
    } catch (Exception e) {
      log.warn("Failed to publish event for intent={} user={}: {}", intent, userId, e.getMessage());
    }
  }

  private String enrichWithAnalytics(
      String userId, String sessionId, String message, String enrichedMessage) {
    try {
      String analytics = insightDataService.buildContext(userId, sessionId);
      if (analytics != null && !analytics.isBlank()) {
        return analytics + "\n\nUser message: " + message;
      }
    } catch (Exception e) {
      log.warn(
          "Failed to enrich insights with analytics for user={} session={}: {}",
          userId,
          sessionId,
          e.getMessage());
    }
    return enrichedMessage;
  }

  private void writeAgentScopeKey(String intent, String agentResponse, AgenticScope scope) {
    if (agentResponse == null || agentResponse.isBlank()) return;
    switch (IntentType.fromName(intent)) {
      case CONTENT_ANALYSIS -> scope.writeState("analysis", agentResponse);
      case ASSESSMENT -> scope.writeState("assessment", agentResponse);
      case INSIGHT -> scope.writeState("insights", agentResponse);
      default -> {}
    }
  }

  private void attachQuizMetadata(
      String intent, String sessionId, String message, String agentResponse, AgenticScope scope, String userId) {
    if (!INTENT_ASSESSMENT.equals(intent) || agentResponse == null || agentResponse.isBlank()) return;
    List<QuizItem> items = parseQuizItems(agentResponse);
    if (items.isEmpty()) {
      log.debug("Assessment output not parseable as structured quiz for session={}", sessionId);
      return;
    }
    String contentId = null;
    try {
      contentId = resolveActiveDocumentId(message, sessionId, userId);
    } catch (Exception e) {
      log.warn("Failed to resolve content id for quiz metadata session={}: {}", sessionId, e.getMessage());
    }
    scope.writeState(
        "quizMetadata",
        new QuizMetadata(contentId, parseQuestionCount(message), parseDifficulty(message), items));
  }

  List<QuizItem> parseQuizItems(String output) {
    if (output == null || output.isBlank()) return List.of();
    try {
      return objectMapper.readValue(
          stripCodeFences(output),
          new com.fasterxml.jackson.core.type.TypeReference<List<QuizItem>>() {});
    } catch (Exception e) {
      log.debug("Quiz output parse failed: {}", e.getMessage());
      return List.of();
    }
  }

  private static String stripCodeFences(String output) {
    String trimmed = output.trim();
    if (trimmed.startsWith("```")) {
      trimmed = trimmed.replaceFirst("^```[a-zA-Z]*\\s*", "");
      trimmed = trimmed.replaceFirst("```\\s*$", "").trim();
    }
    int start = trimmed.indexOf('[');
    int end = trimmed.lastIndexOf(']');
    if (start >= 0 && end > start) {
      trimmed = trimmed.substring(start, end + 1);
    }
    return trimmed;
  }

  private String enrichWithContext(
    String intent, String message, String sessionId, String userId, AgenticScope scope) {
    if (!IntentType.isAnalysis(intent)) return message;

    if (INTENT_CONTENT_ANALYSIS.equals(intent)) {
      if (message.startsWith(UPLOAD_PREFIX)) return enrichUploadAnalysis(message, userId);
      String activeDocId = resolveActiveDocumentId(message, sessionId, userId);
      List<RetrievedChunk> context =
          retrieveScopedContext(userId, sessionId, message, activeDocId, ANALYSIS_TOP_K);
      if (!context.isEmpty()) {
        writeChunkContext(scope, activeDocId, context);
        return numberedContext(context) + "\n\nUser message: " + message;
      }
      return message;
    }

    String activeDocId = resolveActiveDocumentId(message, sessionId, userId);
    List<RetrievedChunk> context =
        retrieveScopedContext(userId, sessionId, message, activeDocId, 3);
    if (!context.isEmpty()) {
      writeChunkContext(scope, activeDocId, context);
      return numberedContext(context) + "\n\nUser message: " + message;
    }
    return message;
  }

  private void writeChunkContext(
      AgenticScope scope, String activeDocId, List<RetrievedChunk> chunks) {
    if (chunks == null || chunks.isEmpty()) return;
    scope.writeState("chunks", chunks.stream().filter(c -> c != null).toList());
    String name = activeDocId;
    try {
      if (activeDocId != null) {
        name = contentDocumentService.resolveFileName(activeDocId);
      }
    } catch (Exception e) {
      log.warn("Failed to resolve filename for citation doc={}: {}", activeDocId, e.getMessage());
    }
    scope.writeState("chunkDocumentName", name);
  }

  private static String numberedContext(List<RetrievedChunk> chunks) {
    StringBuilder sb = new StringBuilder("Relevant context from your uploaded document:");
    int n = 1;
    for (RetrievedChunk c : chunks) {
      if (c == null || c.text() == null || c.text().isBlank()) continue;
      sb.append("\n[").append(n).append("] ").append(c.text());
      n++;
    }
    return sb.toString().trim();
  }

  private String enrichUploadAnalysis(String message, String userId) {
    String docId = message.substring(UPLOAD_PREFIX.length()).trim();
    try {
      List<String> chunks = contentDocumentService.chunkContent(docId, CHUNK_SIZE, CHUNK_OVERLAP);
      if (!chunks.isEmpty()) {
        vectorDBService.ingestDocumentChunks(chunks, docId, "document");
        String contentBody = resolveUploadedContent(message);
        List<RetrievedChunk> context =
            vectorDBService.retrieveRelevantContext(
                contentBody != null ? contentBody : message,
                ANALYSIS_TOP_K,
                VectorSourceKeys.document(docId));
        if (!context.isEmpty()) {
          return "File: "
              + contentDocumentService.resolveFileName(docId)
              + "\n\nRelevant content excerpts:\n"
              + String.join(
                  "\n---\n",
                  context.stream().map(RetrievedChunk::text).toList());
        }
      }
    } catch (Exception e) {
      log.warn("Chunk ingestion failed for docId={} user={}: {}", docId, userId, e.getMessage());
    }
    String contentBody = resolveUploadedContent(message);
    return contentBody != null ? contentBody : message;
  }

  private String resolveAnalysisContext(
      String intent, String message, String sessionId, String userId) {
    if (!INTENT_ASSESSMENT.equals(intent)) return "";
    StringBuilder ctx = new StringBuilder();
    try {
      List<ChatMessage> analysisMem =
          chatMemoryStore.getMessages(ChatMemoryKeys.analysis(sessionId));
      if (analysisMem == null || analysisMem.isEmpty()) {
        // The upload flow runs CAA under session "upload:<docId>"; fall back to it so a
        // quiz request in the chat thread is still grounded in the doc analysis.
        String activeDocId = resolveActiveDocumentId(message, sessionId, userId);
        if (activeDocId != null) {
          analysisMem = chatMemoryStore.getMessages(ChatMemoryKeys.analysis("upload:" + activeDocId));
        }
      }
      if (analysisMem != null) {
        for (ChatMessage m : analysisMem) {
          if (m instanceof AiMessage aiMsg && aiMsg.text() != null) {
            ctx.append(aiMsg.text()).append("\n---\n");
          }
        }
      }
    } catch (Exception e) {
      log.warn(
          "Failed to read CAA analysis from memory for session={}: {}", sessionId, e.getMessage());
    }
    String activeDocId = resolveActiveDocumentId(message, sessionId, userId);
    List<RetrievedChunk> chunks = retrieveScopedContext(userId, sessionId, message, activeDocId, 3);
    if (!chunks.isEmpty()) {
      ctx.append(String.join("\n---\n", chunks.stream().map(RetrievedChunk::text).toList()));
    }
    return ctx.toString();
  }

  private boolean isExplicitVideoLink(String message) {
    return message != null && EXPLICIT_VIDEO_LINK.matcher(message).find();
  }

  private String resolveActiveDocumentId(String message, String sessionId, String userId) {
    if (message.startsWith(ASSESS_PREFIX)) {
      String docId = assessmentTargetId(message);
      if (!docId.isEmpty()) return docId;
    }
    if (sessionId != null) {
      try {
        String fromContent = contentDocumentService.resolveRecentDocumentId(userId, sessionId);
        if (fromContent != null && !fromContent.isEmpty()) return fromContent;
      } catch (Exception e) {
        log.warn(
            "Failed to resolve recent document for session={} user={}: {}",
            sessionId,
            userId,
            e.getMessage());
      }
      try {
        String fromHistory = conversationRepository.lastUploadedDocumentId(userId, sessionId);
        if (fromHistory != null && !fromHistory.isEmpty()) return fromHistory;
      } catch (Exception e) {
        log.warn(
            "Failed to resolve active document for session={} user={}: {}",
            sessionId,
            userId,
            e.getMessage());
      }
    }
    try {
      String fromUser = contentDocumentService.resolveRecentDocumentId(userId, null);
      if (fromUser != null && !fromUser.isEmpty()) return fromUser;
    } catch (Exception e) {
      log.warn("Failed to resolve recent user document for user={}: {}", userId, e.getMessage());
    }
    return null;
  }

  private List<RetrievedChunk> retrieveScopedContext(
      String userId, String sessionId, String message, String activeDocId, int topK) {
    if (activeDocId == null) return List.of();
    try {
      return vectorDBService.retrieveRelevantContext(
          message, topK, VectorSourceKeys.document(activeDocId));
    } catch (Exception e) {
      log.warn(
          "Scoped context retrieval failed for user={} session={}: {}",
          userId,
          sessionId,
          e.getMessage());
      return List.of();
    }
  }

  private String tryVideoSearch(String message, String sessionId, String userId) {
    String messageTopic = youTubeSearchService.extractQuery(message);
    String contextTopic = resolveTopicFromContext(userId, sessionId);
    if (contextTopic == null || contextTopic.isBlank()) {
      contextTopic = lastUserTopicFromMemory(sessionId);
    }
    String query = mergeVideoTopics(messageTopic, contextTopic);
    if (query == null || query.isBlank()) {
      log.warn("No video topic extractable for message='{}'", message);
      return NO_VIDEOS_BARE;
    }
    List<YouTubeSearchService.VideoResult> results = youTubeSearchService.search(query);
    if (results.isEmpty()) {
      log.warn("No YouTube results for query='{}'", query);
      return NO_VIDEOS_FOR + query + "'. Try searching YouTube manually.";
    }
    StringBuilder sb = new StringBuilder("Here's what I found on YouTube:");
    int n = 1;
    for (YouTubeSearchService.VideoResult result : results) {
      sb.append('\n')
          .append(n++)
          .append(". https://www.youtube.com/watch?v=")
          .append(result.videoId())
          .append(" — ")
          .append(result.title());
    }
    return sb.toString();
  }

  private String verifyAndRetry(
      String intent,
      String sessionId,
      String message,
      String enrichedMessage,
      String analysisCtx,
      String agentResponse,
      String userId) {
    if (agentResponse == null || agentResponse.isBlank()) return agentResponse;

    String context = analysisCtx == null || analysisCtx.isBlank() ? enrichedMessage : analysisCtx;
    if (verifyResponse(message, context, agentResponse)) return agentResponse;

    log.info("Response rejected by verifier for intent={}, regenerating once", intent);
    String retried = regenerate(intent, sessionId, message, enrichedMessage, analysisCtx, userId);
    if (retried == null || retried.isBlank()) {
      log.warn("Retry returned blank response for intent={}, returning fallback", intent);
      return VERIFIER_FALLBACK;
    }
    retried = youTubeLinkValidator.sanitize(retried);
    retried = TextUtils.stripThinking(retried);
    if (verifyResponse(message, context, retried)) return retried;

    log.warn(
        "Regenerated response also rejected by verifier for intent={}, returning fallback", intent);
    return assessmentFallbackIfStructured(intent, retried);
  }

  private String assessmentFallbackIfStructured(String intent, String answer) {
    if (INTENT_ASSESSMENT.equals(intent) && !parseQuizItems(answer).isEmpty()) {
      log.warn(
          "Verifier rejected assessment but quiz is structurally valid, delivering it anyway");
      return answer;
    }
    return VERIFIER_FALLBACK;
  }

  private String regenerate(
      String intent,
      String sessionId,
      String message,
      String enrichedMessage,
      String analysisCtx,
      String userId) {
    if (INTENT_VIDEO_SEARCH.equals(intent)) {
      return tryVideoSearch(message, sessionId, userId);
    }
    return dispatchAgent(intent, sessionId, enrichedMessage, analysisCtx);
  }

  private boolean verifyResponse(String userQuestion, String context, String answer) {
    try {
      String result = responseVerifierAgent.verify(userQuestion, context, answer);
      return isAccepted(result);
    } catch (Exception e) {
      log.warn("Response verifier raised an error, failing closed: {}", e.getMessage());
      return false;
    }
  }

  boolean isAccepted(String result) {
    if (result == null || result.isBlank()) {
      log.warn("Verifier returned no verdict, failing closed");
      return false;
    }
    try {
      String candidate = TextUtils.extractJsonObject(TextUtils.stripThinking(result));
      JsonNode parsed = objectMapper.readTree(candidate);
      JsonNode verdict = parsed.get("verdict");
      if (verdict == null || verdict.asText().isBlank()) {
        log.warn("Verifier output had no verdict field, failing closed: {}", snippet(result));
        return false;
      }
      String decision = verdict.asText().trim();
      JsonNode reason = parsed.get("reason");
      if ("ACCEPT".equalsIgnoreCase(decision)) {
        log.info("Verifier accepted answer: {}", reason == null ? "" : reason.asText());
        return true;
      }
      log.warn(
          "Verifier rejected answer ({}): {}",
          decision,
          reason == null ? "" : reason.asText().trim());
      return false;
    } catch (Exception e) {
      log.warn("Verifier output was not valid JSON, failing closed: {}", snippet(result));
      return false;
    }
  }

  private static String snippet(String result) {
    if (result == null) return "";
    return result.length() > 160 ? result.substring(0, 160) + "..." : result;
  }

  private String mergeVideoTopics(String messageTopic, String contextTopic) {
    String m = messageTopic == null ? "" : messageTopic.trim();
    String c = contextTopic == null ? "" : contextTopic.trim();
    if (m.isBlank()) return c.isBlank() ? null : c;
    if (c.isBlank() || m.equalsIgnoreCase(c)) return m;
    if (containsWord(m, c)) return m;
    if (containsWord(c, m)) return c;
    return m + " " + c;
  }

  private boolean containsWord(String text, String word) {
    return Pattern.compile("(?i)(?<![a-z0-9])" + Pattern.quote(word) + "(?![a-z0-9])")
        .matcher(text)
        .find();
  }

  private String resolveTopicFromContext(String userId, String sessionId) {
    try {
      ChatHistory history = conversationRepository.getHistory(userId, sessionId);
      if (history == null || history.messages() == null || history.messages().isEmpty()) {
        return null;
      }
      List<ChatHistory.ChatMessage> messages = history.messages();
      for (int i = messages.size() - 1; i >= 0; i--) {
        ChatHistory.ChatMessage msg = messages.get(i);
        if (msg == null || !ChatRole.isUser(msg.role())) continue;
        if (msg.content() == null || msg.content().isBlank()) continue;
        String topic = youTubeSearchService.extractQuery(msg.content());
        if (topic != null && !topic.isBlank()) return topic;
      }
    } catch (Exception e) {
      log.warn(
          "Failed to resolve topic from history for session={}: {}", sessionId, e.getMessage());
    }
    return null;
  }

  private String lastUserTopicFromMemory(String sessionId) {
    if (sessionId == null || sessionId.isBlank() || chatMemoryStore == null) return null;
    try {
      List<ChatMessage> messages =
          chatMemoryStore.getMessages(ChatMemoryKeys.conversation(sessionId));
      for (int i = messages.size() - 1; i >= 0; i--) {
        ChatMessage msg = messages.get(i);
        if (msg.type() != ChatMessageType.USER) continue;
        String text = ((UserMessage) msg).singleText();
        if (text == null || text.isBlank()) continue;
        String topic = youTubeSearchService.extractQuery(text);
        if (topic != null && !topic.isBlank()) return topic;
      }
    } catch (Exception e) {
      log.warn(
          "Failed to read chat memory for topic fallback session={}: {}",
          sessionId,
          e.getMessage());
    }
    return null;
  }

  private String resolveUploadedContent(String message) {
    if (!message.startsWith(UPLOAD_PREFIX)) return null;
    String docId = message.substring(UPLOAD_PREFIX.length()).trim();
    return contentDocumentService.resolveContent(docId);
  }

  private void scheduleTitleGeneration(String userId, String sessionId) {
    if (executor == null || titleGenerator == null) return;
    try {
      executor.execute(() -> asyncJobRunner.run(() -> generateTitle(userId, sessionId)));
    } catch (Exception e) {
      log.warn("Failed to schedule title generation for session={}: {}", sessionId, e.getMessage());
    }
  }

  private void generateTitle(String userId, String sessionId) {
    try {
      ConversationLog first = conversationRepository.firstUserMessage(userId, sessionId);
      if (first == null) return;
      String title = null;
      try {
        title = titleGenerator.generate(first.message);
      } catch (Exception e) {
        log.warn("LLM title generation failed for session={}: {}", sessionId, e.getMessage());
      }
      if (title == null || title.isBlank()) {
        title = fallbackTitle(first.message);
      }
      conversationRepository.setThreadTitle(userId, sessionId, title);
    } catch (Exception e) {
      log.warn("Title generation failed for session={}: {}", sessionId, e.getMessage());
    }
  }

  private String fallbackTitle(String message) {
    String collapsed = message.replaceAll("\\s+", " ").trim();
    if (collapsed.isEmpty()) return "New chat";
    return collapsed.length() <= 40 ? collapsed : collapsed.substring(0, 40).trim() + "…";
  }

  @Transactional
  public void recordQuizResult(String userId, QuizResultRequest request) {
    if (userId == null || request == null) {
      throw new IllegalArgumentException("Quiz result requires a user id and payload");
    }
    if (request.questions() == null || request.questions().isEmpty()) {
      throw new IllegalArgumentException("Quiz result requires at least one question");
    }
    QuizResult result = new QuizResult();
    result.userId = userId;
    result.sessionId = request.sessionId();
    result.contentId = request.contentId();
    result.score = Math.max(0, Math.min(request.score(), request.total()));
    result.total = request.total();
    try {
      result.questions = objectMapper.writeValueAsString(request.questions());
      result.answers =
          objectMapper.writeValueAsString(
              request.answers() == null ? Map.of() : request.answers());
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to serialize quiz payload", e);
    }
    quizResultRepository.save(result);
    feedProfilingFromQuiz(userId, request);
  }

  private void feedProfilingFromQuiz(String userId, QuizResultRequest request) {
    int total = request.total() <= 0 ? request.questions().size() : request.total();
    if (total <= 0 || request.score() * 2 >= total) return;

    List<String> missed = new ArrayList<>();
    for (QuizItem item : request.questions()) {
      String userAnswer = request.answers() == null ? null : request.answers().get(item.question());
      if (userAnswer == null || item.answer() == null || !userAnswer.equalsIgnoreCase(item.answer())) {
        missed.add(item.question());
      }
    }
    String note =
        "[%s] Quiz %d/%d — recheck: %s"
            .formatted(Instant.now(), request.score(), total, String.join(" | ", missed));
    UserProfile profile = userProfileRepository.findOrCreate(userId);
    String existing = profile.behavioralTraits;
    String separator = (existing == null || existing.isBlank()) ? "" : "\n";
    profile.behavioralTraits = (existing == null ? "" : existing) + separator + note;
    log.info("Recorded weak-area quiz feedback for user={} score={}/{}", userId, request.score(), total);
  }
}
