package com.ailms.orchestrator.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.declarative.ErrorHandler;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface QuizVerifierAgent {

  @SystemMessage(
      """
      You fact-check multiple-choice quizzes generated from a source document.

      You receive:
      - source_context: the only material the quiz may draw on (the document analysis).
      - quiz: a JSON array of question objects, each with "question", "type",
        "options", "answer" and "explanation".

      For EVERY question, check all of the following against the source_context:
      1. The marked "answer" is factually CORRECT.
      2. The marked "answer" is supported by the source_context (not outside knowledge).
         A fact that contradicts the source_context makes the question invalid, even if
         it is true in general.
      3. For multiple_choice and true_false, the "answer" exactly matches one of the
         provided "options".
      4. The "explanation" is consistent with the source_context.

      Accept ONLY if every question passes all checks. If any question fails, reject and
      name the offending question(s) and the exact error so a regeneration can fix them.
      Be strict: a quiz whose answer key contradicts the source_context must be rejected.

      Respond ONLY in this exact JSON shape (no surrounding text):
      {"verdict": "ACCEPT" | "NEEDS_REWRITE", "reason": "short explanation naming bad questions"}
      """)
  @Agent(
      name = "QuizVerifierAgent",
      description = "Fact-checks generated quiz questions and answers against the source context",
      outputKey = "quizVerification")
  @UserMessage(
      """
      source_context:
      {{sourceContext}}

      quiz:
      {{quiz}}
      """)
  String verify(@V("sourceContext") String sourceContext, @V("quiz") String quiz);

  @ErrorHandler
  static ErrorRecoveryResult onError(ErrorContext ctx) {
    return ErrorRecoveryResult.result(
        "{\"verdict\": \"NEEDS_REWRITE\", \"reason\": \"quiz fact-checker unavailable\"}");
  }
}
