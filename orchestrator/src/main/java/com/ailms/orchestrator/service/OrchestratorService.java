package com.ailms.orchestrator.service;

import com.ailms.common.constants.ChatMemoryKeys;
import com.ailms.common.constants.PromptPrefixes;
import com.ailms.common.constants.VectorSourceKeys;
import com.ailms.common.dto.ChatHistory;
import com.ailms.common.dto.ChatRequest;
import com.ailms.common.dto.ChatResponse;
import com.ailms.common.entity.ConversationLog;
import com.ailms.common.enums.ChatRole;
import com.ailms.common.enums.IntentType;
import com.ailms.orchestrator.agent.ContentAnalysisAgent;
import com.ailms.orchestrator.agent.ConversationAgent;
import com.ailms.orchestrator.agent.InsightAgent;
import com.ailms.orchestrator.agent.IntentClassifier;
import com.ailms.orchestrator.agent.ProfilingAgent;
import com.ailms.orchestrator.agent.ProactiveFollowUpAgent;
import com.ailms.orchestrator.agent.QuestionGenerationAgent;
import com.ailms.orchestrator.agent.ResponseComposer;
import com.ailms.orchestrator.agent.ResponseVerifierAgent;
import com.ailms.orchestrator.agent.TitleGenerator;
import com.ailms.orchestrator.repository.ConversationRepository;
import com.ailms.orchestrator.util.TextUtils;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.LangChain4jManaged;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.context.ManagedExecutor;

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

  @Inject VectorDBService vectorDBService;

  @Inject ContentDocumentService contentDocumentService;

  @Inject YouTubeLinkValidator youTubeLinkValidator;

  @Inject YouTubeSearchService youTubeSearchService;

  @Inject TitleGenerator titleGenerator;

  @Inject ProactiveFollowUpAgent proactiveFollowUpAgent;

  @Inject ResponseVerifierAgent responseVerifierAgent;

  @Inject ManagedExecutor executor;

  @Inject RedisChatMemoryStore chatMemoryStore;

  private static final Pattern VIDEO_REQUEST = Pattern.compile("(?i)(?:youtube|youtu\\.be)");

  private static final Pattern DOC_REFERENCE =
      Pattern.compile(
          "(?i)\\b(?:the|this|that|my)\\s+(?:document|pdf)\\b"
              + "|(?i)\\b(?:uploaded|attached)\\s+(?:document|file|pdf)\\b"
              + "|(?i)\\b(?:the|this|that|my)\\s+uploaded\\s+file\\b"
              + "|(?i)\\bbased\\s+on\\s+(?:the|this|that|my)\\s+(?:document|file|pdf)\\b");

  private static final Pattern QUESTION_REFERENCE =
      Pattern.compile("(?i)\\bquestion(?:s)?\\b|(?i)\\bquiz(?:zes)?\\b|(?i)\\bassess(?:ment)?\\b|(?i)\\btest\\b");

  private static final String NO_DOCUMENT_MESSAGE =
      "I couldn't find an uploaded document in this conversation. "
          + "Upload a file first, and then I can analyze it or generate questions from it.";

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

  private static final String UPLOAD_PREFIX = PromptPrefixes.UPLOAD_ANALYSIS;

  private static final String ASSESS_PREFIX = "Generate assessment for content ";

  private static final int CHUNK_SIZE = 800;

  private static final int CHUNK_OVERLAP = 100;

  private static final int ANALYSIS_TOP_K = 8;

  @Inject KafkaEventPublisher kafkaEventPublisher;

  public ChatResponse route(ChatRequest request, String userId) {
    profilingService.ensureProfile(userId);

    String sessionId = request.sessionId();
    if (sessionId == null || sessionId.isBlank()) {
      sessionId = java.util.UUID.randomUUID().toString();
      log.info("Generated session id for user={}: {}", userId, sessionId);
    }

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
      } else if (VIDEO_REQUEST.matcher(message).find()) {
        intent = INTENT_VIDEO_SEARCH;
        enrichedMessage = message;
        log.info(
            "Intent={} (keyword short-circuit) for user={} message={}",
            INTENT_VIDEO_SEARCH,
            userId,
            message);
      } else if (isDocumentReference(message)) {
        String activeDocId = resolveActiveDocumentId(message, sessionId, userId);
        if (activeDocId != null) {
          intent =
              QUESTION_REFERENCE.matcher(message).find()
                  ? INTENT_ASSESSMENT
                  : INTENT_CONTENT_ANALYSIS;
          enrichedMessage = enrichWithContext(intent, message, sessionId, userId);
          log.info(
              "Intent={} (document short-circuit doc={}) for user={} message={}",
              intent,
              activeDocId,
              userId,
              message);
        } else {
          intent = INTENT_CONVERSATION;
          enrichedMessage = message;
          agentResponse = NO_DOCUMENT_MESSAGE;
          log.warn(
              "Document referenced but none found for user={} session={} message={}",
              userId,
              sessionId,
              message);
        }
      } else {
        intent = normalizeIntent(intentClassifier.classify(message));
        intent = reclassifyContentIntent(intent, message, sessionId, userId);
        log.info("Intent={} for user={} message={}", intent, userId, message);
        enrichedMessage = enrichWithContext(intent, message, sessionId, userId);
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

      agentResponse =
          verifyAndRetry(intent, sessionId, message, enrichedMessage, analysisCtx, agentResponse, userId);

      if (agentResponse == null || agentResponse.isBlank()) {
        log.warn("Router returned blank response for intent={} user={}", intent, userId);
      }

      if (greetingResponse == null) {
        try {
          String profileUpdate =
              profilingAgent.process(ChatMemoryKeys.profiling(sessionId), enrichedMessage);
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
      ChatResponse response = responseComposer.compose(scope, sessionId);

      boolean isNewSession =
          conversationRepository.count(
                  "sessionId = ?1 AND (deleted IS NULL OR deleted = false)", sessionId)
              == 0;
      conversationRepository.logMessage(
          userId, sessionId, ChatRole.USER.key(), request.message());
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
          "No session document for intent={}, reclassifying to {}",
          intent,
          INTENT_CONVERSATION);
      return INTENT_CONVERSATION;
    }
    return intent;
  }

  private String dispatchAgent(
      String intent, String sessionId, String message, String analysisCtx) {
    String memoryId = ChatMemoryKeys.conversation(sessionId);
    return switch (IntentType.fromName(intent)) {
      case CONTENT_ANALYSIS -> contentAnalysisAgent.process(memoryId, message);
      case ASSESSMENT -> questionGenerationAgent.process(memoryId, message, analysisCtx);
      case INSIGHT -> insightAgent.process(memoryId, message);
      default -> conversationAgent.process(memoryId, message);
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

  private String enrichWithContext(String intent, String message, String sessionId, String userId) {
    if (!IntentType.isAnalysis(intent)) return message;

    if (INTENT_CONTENT_ANALYSIS.equals(intent)) {
      if (message.startsWith(UPLOAD_PREFIX)) return enrichUploadAnalysis(message, userId);
      String activeDocId = resolveActiveDocumentId(message, sessionId, userId);
      List<String> context =
          retrieveScopedContext(userId, sessionId, message, activeDocId, ANALYSIS_TOP_K);
      if (!context.isEmpty()) {
        return "Relevant context:\n"
            + String.join("\n---\n", context)
            + "\n\nUser message: "
            + message;
      }
      return message;
    }

    String activeDocId = resolveActiveDocumentId(message, sessionId, userId);
    List<String> context = retrieveScopedContext(userId, sessionId, message, activeDocId, 3);
    if (!context.isEmpty()) {
      return "Relevant context:\n"
          + String.join("\n---\n", context)
          + "\n\nUser message: "
          + message;
    }
    return message;
  }

  private String enrichUploadAnalysis(String message, String userId) {
    String docId = message.substring(UPLOAD_PREFIX.length()).trim();
    try {
      List<String> chunks = contentDocumentService.chunkContent(docId, CHUNK_SIZE, CHUNK_OVERLAP);
      if (!chunks.isEmpty()) {
        vectorDBService.ingestDocumentChunks(chunks, docId, "document");
        String contentBody = resolveUploadedContent(message);
        List<String> context =
            vectorDBService.retrieveRelevantContext(
                contentBody != null ? contentBody : message,
                ANALYSIS_TOP_K,
                VectorSourceKeys.document(docId));
        if (!context.isEmpty()) {
          return "File: "
              + contentDocumentService.resolveFileName(docId)
              + "\n\nRelevant content excerpts:\n"
              + String.join("\n---\n", context);
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
    String activeDocId = resolveActiveDocumentId(message, sessionId, userId);
    List<String> context = retrieveScopedContext(userId, sessionId, message, activeDocId, 3);
    if (!context.isEmpty()) {
      return String.join("\n---\n", context);
    }
    return "";
  }

  private boolean isDocumentReference(String message) {
    if (message.startsWith(UPLOAD_PREFIX) || message.startsWith(ASSESS_PREFIX)) return false;
    return DOC_REFERENCE.matcher(message).find();
  }

  private String resolveActiveDocumentId(String message, String sessionId, String userId) {
    if (message.startsWith(ASSESS_PREFIX)) {
      String docId = message.substring(ASSESS_PREFIX.length()).trim();
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
      log.warn(
          "Failed to resolve recent user document for user={}: {}", userId, e.getMessage());
    }
    return null;
  }

  private List<String> retrieveScopedContext(
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

    log.info(
        "Response rejected by verifier for intent={}, regenerating once", intent);
    String retried =
        regenerate(intent, sessionId, message, enrichedMessage, analysisCtx, userId);
    if (retried == null || retried.isBlank()) {
      log.warn("Retry returned blank response for intent={}, keeping original", intent);
      return agentResponse;
    }
    retried = youTubeLinkValidator.sanitize(retried);
    retried = TextUtils.stripThinking(retried);
    if (verifyResponse(message, context, retried)) return retried;

    log.warn("Regenerated response also rejected by verifier for intent={}, keeping it", intent);
    return retried;
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
      log.warn("Response verifier failed, accepting response: {}", e.getMessage());
      return true;
    }
  }

  private boolean isAccepted(String result) {
    if (result == null) return true;
    return !result.toUpperCase(Locale.ROOT).contains("\"NEEDS_REWRITE\"")
        && !result.toUpperCase(Locale.ROOT).contains("NEEDS_REWRITE");
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
          "Failed to resolve topic from history for session={}: {}",
          sessionId,
          e.getMessage());
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

  public String generateProactiveMessage(String userId, String context) {
    AgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
    LangChain4jManaged.setCurrent(Map.of(AgenticScope.class, scope));
    try {
      return proactiveFollowUpAgent.generate(context);
    } finally {
      LangChain4jManaged.removeCurrent();
    }
  }

  private void scheduleTitleGeneration(String userId, String sessionId) {
    if (executor == null || titleGenerator == null) return;
    try {
      executor.execute(() -> generateTitle(userId, sessionId));
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
}
