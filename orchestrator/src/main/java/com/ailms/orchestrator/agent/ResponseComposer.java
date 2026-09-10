package com.ailms.orchestrator.agent;

import com.ailms.common.dto.ChatResponse;
import com.ailms.common.dto.Citation;
import com.ailms.common.dto.CitationMetadata;
import com.ailms.common.dto.RetrievedChunk;
import com.ailms.common.enums.IntentType;
import dev.langchain4j.agentic.scope.AgenticScope;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class ResponseComposer {

  private static final Pattern CITATION_MARKER = Pattern.compile("\\[(\\d{1,3})\\]");

  public ChatResponse compose(AgenticScope agenticScope, String sessionId) {
    String intent = agenticScope.readState("intent", IntentType.CONVERSATION.name());
    String response = extractResponse(agenticScope, intent);
    Object metadata = agenticScope.readState("quizMetadata", null);
    if (metadata == null) {
      metadata = buildCitationMetadata(agenticScope, response);
    }

    return new ChatResponse(response, sessionId, intent, metadata);
  }

  private Object buildCitationMetadata(AgenticScope agenticScope, String response) {
    if (response == null || response.isBlank()) return null;
    List<RetrievedChunk> chunks = agenticScope.readState("chunks", List.of());
    if (chunks == null || chunks.isEmpty()) return null;

    String documentName = agenticScope.readState("chunkDocumentName", null);
    Set<Integer> seen = new LinkedHashSet<>();
    Matcher matcher = CITATION_MARKER.matcher(response);
    while (matcher.find()) {
      int n = Integer.parseInt(matcher.group(1));
      if (n >= 1 && n <= chunks.size()) seen.add(n);
    }
    if (seen.isEmpty()) return null;

    List<Citation> citations = new ArrayList<>(seen.size());
    for (int n : seen) {
      RetrievedChunk chunk = chunks.get(n - 1);
      citations.add(
          new Citation(
              n,
              chunk.source(),
              documentName == null ? chunk.source() : documentName,
              chunk.chunkIndex(),
              chunk.text()));
    }
    return new CitationMetadata(citations);
  }

  private String extractResponse(AgenticScope agenticScope, String intent) {
    String primary = agenticScope.readState("response", "");
    if (primary != null && !primary.isBlank()) return primary;

    return switch (IntentType.fromName(intent)) {
      case CONTENT_ANALYSIS ->
          fallback(
              agenticScope.readState("analysis", ""),
              "I couldn't analyze because no analyzable content was provided. "
                  + "Upload a file or paste text first.");
      case ASSESSMENT ->
          fallback(
              agenticScope.readState("assessment", ""),
              "I couldn't generate questions because no content was provided. "
                  + "Upload a file or paste text first.");
      case INSIGHT ->
          fallback(
              agenticScope.readState("insights", ""),
              "Not enough data to generate insights yet. Complete a few lessons and try again.");
      default ->
          fallback(primary, "I couldn't generate a response. Please try rephrasing your question.");
    };
  }

  private String fallback(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
