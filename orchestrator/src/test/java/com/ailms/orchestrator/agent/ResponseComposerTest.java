package com.ailms.orchestrator.agent;

import static org.junit.jupiter.api.Assertions.*;

import com.ailms.common.dto.ChatResponse;
import com.ailms.common.dto.QuizItem;
import com.ailms.common.dto.QuizMetadata;
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
}