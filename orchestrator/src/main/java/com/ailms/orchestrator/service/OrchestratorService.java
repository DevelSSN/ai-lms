package com.ailms.orchestrator.service;

import com.ailms.common.dto.ChatRequest;
import com.ailms.common.dto.ChatResponse;
import com.ailms.common.entity.ConversationLog;
import com.ailms.orchestrator.agent.ContentAnalysisAgent;
import com.ailms.orchestrator.agent.ConversationAgent;
import com.ailms.orchestrator.agent.InsightAgent;
import com.ailms.orchestrator.agent.IntentClassifier;
import com.ailms.orchestrator.agent.ProfilingAgent;
import com.ailms.orchestrator.agent.QuestionGenerationAgent;
import com.ailms.orchestrator.agent.ResponseComposer;
import com.ailms.orchestrator.agent.TitleGenerator;
import com.ailms.orchestrator.repository.ConversationRepository;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.invocation.LangChain4jManaged;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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

  @Inject ManagedExecutor executor;

  private static final Set<String> KNOWN_INTENTS =
      Set.of("CONVERSATION", "VIDEO_SEARCH", "CONTENT_ANALYSIS", "ASSESSMENT", "INSIGHT");

  @Inject KafkaEventPublisher kafkaEventPublisher;

  public ChatResponse route(ChatRequest request, String userId) {
    profilingService.ensureProfile(userId);

    AgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
    LangChain4jManaged.setCurrent(Map.of(AgenticScope.class, scope));
    try {
      String intent = normalizeIntent(intentClassifier.classify(request.message()));
      log.info("Intent={} for user={} message={}", intent, userId, request.message());

      String enrichedMessage = enrichWithContext(intent, request.message(), userId);

      scope.writeState("intent", intent);

      String analysisCtx = resolveAnalysisContext(intent, request.message(), userId);
      String agentResponse = null;
      if ("VIDEO_SEARCH".equals(intent)) {
        agentResponse = tryVideoSearch(request.message());
        if (agentResponse == null) {
          intent = "CONVERSATION";
          scope.writeState("intent", intent);
        }
      }
      if (agentResponse == null) {
        agentResponse = dispatchAgent(intent, request.sessionId(), enrichedMessage, analysisCtx);
      }
      agentResponse = youTubeLinkValidator.sanitize(agentResponse);

      if (agentResponse == null || agentResponse.isBlank()) {
        log.warn("Router returned blank response for intent={} user={}", intent, userId);
      }

      profilingAgent.process("profiling:" + request.sessionId(), enrichedMessage);

      scope.writeState("response", agentResponse);
      ChatResponse response = responseComposer.compose(scope, request.sessionId());

      boolean isNewSession =
          conversationRepository.count(
                  "sessionId = ?1 AND (deleted IS NULL OR deleted = false)", request.sessionId())
              == 0;
      conversationRepository.logMessage(userId, request.sessionId(), "user", request.message());
      conversationRepository.logMessage(
          userId, request.sessionId(), "assistant", response.message(), response.agentType());

      if (isNewSession) scheduleTitleGeneration(userId, request.sessionId());

      ingestIfContent(intent, request.message(), userId);

      publishAgentEvent(intent, userId, request.sessionId(), response.message());

      log.info("Response ready for user={} type={}", userId, intent);
      return response;
    } finally {
      LangChain4jManaged.removeCurrent();
    }
  }

  private String normalizeIntent(String raw) {
    if (raw == null) return "CONVERSATION";
    String normalized = raw.trim().toUpperCase(Locale.ROOT).replaceFirst("\\.$", "");
    if (!KNOWN_INTENTS.contains(normalized)) {
      log.warn("Unrecognized classifier output '{}', defaulting to CONVERSATION", raw);
      return "CONVERSATION";
    }
    return normalized;
  }

  private String dispatchAgent(
      String intent, String sessionId, String message, String analysisCtx) {
    String memoryId = "conversation:" + sessionId;
    return switch (intent) {
      case "CONTENT_ANALYSIS" -> contentAnalysisAgent.process(memoryId, message);
      case "ASSESSMENT" -> questionGenerationAgent.process(memoryId, message, analysisCtx);
      case "INSIGHT" -> insightAgent.process(memoryId, message);
      default -> conversationAgent.process(memoryId, message);
    };
  }

  private void publishAgentEvent(String intent, String userId, String sessionId, String message) {
    try {
      switch (intent) {
        case "CONTENT_ANALYSIS" ->
            kafkaEventPublisher.publishContentAnalysisComplete(userId, sessionId, message);
        case "INSIGHT" -> kafkaEventPublisher.publishInsightGenerated(userId, sessionId, message);
        default -> {}
      }
    } catch (Exception e) {
      log.warn("Failed to publish event for intent={} user={}: {}", intent, userId, e.getMessage());
    }
  }

  private String enrichWithContext(String intent, String message, String userId) {
    if (!"CONTENT_ANALYSIS".equals(intent) && !"ASSESSMENT".equals(intent)) return message;

    String contentBody = resolveUploadedContent(message);
    String enriched = contentBody != null ? contentBody : message;

    try {
      List<String> context = vectorDBService.retrieveRelevantContext(enriched, 3);
      if (!context.isEmpty()) {
        String ctx = String.join("\n---\n", context);
        return "Relevant context:\n" + ctx + "\n\nContent to analyze: " + enriched;
      }
    } catch (Exception e) {
      log.warn("Qdrant context retrieval failed for user={}: {}", userId, e.getMessage());
    }
    return enriched;
  }

  private String resolveAnalysisContext(String intent, String message, String userId) {
    if (!"ASSESSMENT".equals(intent)) return "";
    try {
      List<String> context = vectorDBService.retrieveRelevantContext(message, 3);
      if (!context.isEmpty()) {
        return String.join("\n---\n", context);
      }
    } catch (Exception e) {
      log.warn("Context retrieval failed for user={}: {}", userId, e.getMessage());
    }
    return "";
  }

  private String tryVideoSearch(String message) {
    String query = youTubeSearchService.extractQuery(message);
    List<YouTubeSearchService.VideoResult> results = youTubeSearchService.search(query);
    if (results.isEmpty()) return null;
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

  private String resolveUploadedContent(String message) {
    if (!message.startsWith("Analyze the uploaded file: ")) return null;
    String docId = message.substring("Analyze the uploaded file: ".length()).trim();
    return contentDocumentService.resolveContent(docId);
  }

  private void ingestIfContent(String intent, String message, String userId) {
    if (!"CONTENT_ANALYSIS".equals(intent)) return;
    try {
      vectorDBService.ingestDocument(message, "user-" + userId, "conversation");
    } catch (Exception e) {
      log.warn("Qdrant ingestion failed for user={}: {}", userId, e.getMessage());
    }
  }

  public String generateProactiveMessage(String userId, String context) {
    AgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
    LangChain4jManaged.setCurrent(Map.of(AgenticScope.class, scope));
    try {
      return conversationAgent.process(
          "proactive-" + userId,
          "Generate a brief encouraging follow-up message for a student who hasn't been active."
              + " Context: "
              + context);
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
