package com.ailms.orchestrator.service;

import com.ailms.common.dto.ChatRequest;
import com.ailms.common.dto.ChatResponse;
import com.ailms.common.entity.ContentDocument;
import com.ailms.orchestrator.agent.ConversationAgent;
import com.ailms.orchestrator.agent.IntentClassifier;
import com.ailms.orchestrator.agent.OrchestratorRouter;
import com.ailms.orchestrator.agent.ProfilingAgent;
import com.ailms.orchestrator.agent.ResponseComposer;
import com.ailms.orchestrator.repository.ConversationRepository;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.invocation.LangChain4jManaged;
import io.quarkus.hibernate.orm.panache.Panache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@ApplicationScoped
public class OrchestratorService {

  @Inject IntentClassifier intentClassifier;

  @Inject OrchestratorRouter orchestratorRouter;

  @Inject ProfilingAgent profilingAgent;

  @Inject ResponseComposer responseComposer;

  @Inject ConversationAgent conversationAgent;

  @Inject ProfilingService profilingService;

  @Inject ConversationRepository conversationRepository;

  @Inject VectorDBService vectorDBService;

  @Inject ObjectStorageService objectStorage;

  private static final Set<String> KNOWN_INTENTS =
      Set.of("CONVERSATION", "CONTENT_ANALYSIS", "ASSESSMENT", "INSIGHT");

  @Inject DocumentParserService documentParser;

  @Inject KafkaEventPublisher kafkaEventPublisher;

  @Transactional
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
      String agentResponse = orchestratorRouter.route(
          request.sessionId(), enrichedMessage, analysisCtx, intent);

      if (agentResponse == null || agentResponse.isBlank()) {
        log.warn("Router returned blank response for intent={} user={}", intent, userId);
      }

      profilingAgent.process("profiling:" + request.sessionId(), enrichedMessage);

      scope.writeState("response", agentResponse);
      ChatResponse response = responseComposer.compose(scope, request.sessionId());

      conversationRepository.logMessage(userId, request.sessionId(), "user", request.message());
      conversationRepository.logMessage(
          userId, request.sessionId(), "assistant", response.message(), response.agentType());

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

  private void publishAgentEvent(String intent, String userId, String sessionId, String message) {
    try {
      switch (intent) {
        case "CONTENT_ANALYSIS" -> kafkaEventPublisher.publishContentAnalysisComplete(userId, sessionId, message);
        case "INSIGHT" -> kafkaEventPublisher.publishInsightGenerated(userId, sessionId, message);
        default -> {}
      }
    } catch (Exception e) {
      log.warn("Failed to publish event for intent={} user={}: {}", intent, userId, e.getMessage());
    }
  }

  private String enrichWithContext(String intent, String message, String userId) {
    if (!"CONTENT_ANALYSIS".equals(intent) && !"ASSESSMENT".equals(intent)) return message;

    String contentBody = resolveFileContent(message);
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

  private String resolveFileContent(String message) {
    if (!message.startsWith("Analyze the uploaded file: ")) return null;
    String docId = message.substring("Analyze the uploaded file: ".length()).trim();
    try {
      ContentDocument doc = Panache.getEntityManager().find(ContentDocument.class, docId);
      if (doc == null || doc.storagePath == null) return null;
      byte[] fileBytes = objectStorage.readFile(doc.storagePath);
      if (fileBytes == null) return null;

      String content;
      if (documentParser.isSupported(doc.fileType)) {
        DocumentParserService.ParseResult result = documentParser.parse(fileBytes, doc.fileName, doc.fileType);
        if (result.isSuccess()) {
          content = result.text();
          doc.extractedText = content;
          doc.status = "PARSED";
          doc.processedAt = java.time.Instant.now();
          Panache.getEntityManager().merge(doc);
          log.info("Parsed document docId={} type={} textLength={}", docId, doc.fileType, content.length());
        } else {
          content = new String(fileBytes, StandardCharsets.UTF_8);
          log.warn("Parse failed for docId={}: {}, falling back to raw bytes", docId, result.error());
        }
      } else {
        content = new String(fileBytes, StandardCharsets.UTF_8);
        log.info("Unsupported type={} for docId={}, using raw bytes", doc.fileType, docId);
      }

      return "File: " + doc.fileName + "\n\nContent:\n" + content;
    } catch (Exception e) {
      log.warn("Failed to resolve file content for docId={}: {}", docId, e.getMessage());
      return null;
    }
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
          "Generate a brief encouraging follow-up message for a student who hasn't been active. Context: " + context);
    } finally {
      LangChain4jManaged.removeCurrent();
    }
  }
}
