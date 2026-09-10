package com.ailms.orchestrator.agent;

import static org.junit.jupiter.api.Assertions.*;

import com.ailms.common.dto.ChatResponse;
import com.ailms.common.dto.CitationMetadata;
import com.ailms.common.dto.QuizItem;
import com.ailms.common.dto.QuizMetadata;
import com.ailms.common.dto.RetrievedChunk;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResponseComposerTest {

  private final ResponseComposer composer = new ResponseComposer();

  @Test
  void compose_attachesQuizMetadata_whenPresent() {
    AgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
    scope.writeState("intent", "ASSESSMENT");
    scope.writeState("response", "ignored-json");
    scope.writeState(
        "quizMetadata",
        new QuizMetadata(
            "doc-9",
            2,
            "hard",
            List.of(
                new QuizItem(
                    "Q1", "multiple_choice", List.of("A", "B"), "B", "explain"))));

    ChatResponse resp = composer.compose(scope, "sess-1");

    assertEquals("ASSESSMENT", resp.agentType());
    assertInstanceOf(QuizMetadata.class, resp.metadata());
    var meta = (QuizMetadata) resp.metadata();
    assertEquals("doc-9", meta.contentId());
    assertEquals(1, meta.items().size());
  }

  @Test
  void compose_metadataNull_whenNoQuiz() {
    AgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
    scope.writeState("intent", "CONVERSATION");
    scope.writeState("response", "hello");

    ChatResponse resp = composer.compose(scope, "sess-1");

    assertNull(resp.metadata());
  }

  @Test
  void compose_buildsCitationMetadata_fromChunksAndMarkers() {
    AgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
    scope.writeState("intent", "CONVERSATION");
    scope.writeState(
        "response", "Photosynthesis converts light into glucose [1] and oxygen [2].");
    scope.writeState("chunkDocumentName", "photosynthesis.txt");
    scope.writeState(
        "chunks",
        List.of(
            new RetrievedChunk("glucose synthesis", "doc:doc-9", 0.9, 0),
            new RetrievedChunk("oxygen release", "doc:doc-9", 0.8, 1)));

    ChatResponse resp = composer.compose(scope, "sess-1");

    assertInstanceOf(CitationMetadata.class, resp.metadata());
    var meta = (CitationMetadata) resp.metadata();
    assertEquals(2, meta.citations().size());
    assertEquals(1, meta.citations().get(0).number());
    assertEquals("photosynthesis.txt", meta.citations().get(0).documentName());
    assertEquals(0, meta.citations().get(0).chunkIndex());
    assertEquals("glucose synthesis", meta.citations().get(0).text());
    assertEquals("oxygen release", meta.citations().get(1).text());
  }

  @Test
  void compose_citationMetadataIgnoresOutOfRangeMarkers() {
    AgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
    scope.writeState("intent", "CONVERSATION");
    scope.writeState("response", "Only [1] is real [99] is not.");
    scope.writeState("chunkDocumentName", "doc.txt");
    scope.writeState(
        "chunks",
        List.of(new RetrievedChunk("single chunk", "doc:doc-9", 0.9, 0)));

    ChatResponse resp = composer.compose(scope, "sess-1");

    var meta = (CitationMetadata) resp.metadata();
    assertEquals(1, meta.citations().size());
    assertEquals(1, meta.citations().get(0).number());
  }

  @Test
  void compose_metadataNull_whenMarkersWithoutChunks() {
    AgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
    scope.writeState("intent", "CONVERSATION");
    scope.writeState("response", "Says [1] but no chunks stored.");

    ChatResponse resp = composer.compose(scope, "sess-1");

    assertNull(resp.metadata());
  }

  @Test
  void compose_quizMetadataTakesPrecedence_overCitations() {
    AgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
    scope.writeState("intent", "ASSESSMENT");
    scope.writeState("response", "json");
    scope.writeState(
        "chunks", List.of(new RetrievedChunk("ctx", "doc:doc-9", 0.9, 0)));
    scope.writeState(
        "quizMetadata",
        new QuizMetadata(
            "doc-9",
            2,
            "hard",
            List.of(
                new QuizItem(
                    "Q1", "multiple_choice", List.of("A", "B"), "B", "explain"))));

    ChatResponse resp = composer.compose(scope, "sess-1");

    assertInstanceOf(QuizMetadata.class, resp.metadata());
  }
}