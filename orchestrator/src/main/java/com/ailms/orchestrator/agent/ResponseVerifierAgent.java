package com.ailms.orchestrator.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.declarative.ErrorHandler;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(
    chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
public interface ResponseVerifierAgent {

  @SystemMessage(
      """
      You verify whether an assistant's answer to a student is SUFFICIENT and ACCURATE
      for the user's question in an AI-powered Learning Management System.

      You will receive:
      - user_question: the student's latest request
      - user_context: any relevant context (lesson/topic, document excerpt, chat history)
      - assistant_answer: the answer produced before it is sent to the student

      Decide ACCEPT or NEEDS_REWRITE:
      - ACCEPT if the answer is on-topic, factually consistent with the context, and
        meaningfully answers the question.
      - NEEDS_REWRITE if the answer is off-topic, is empty, merely repeats itself without
        answering, introduces facts NOT supported by the context (hallucination), or only
        partially answers while ignoring a clear requirement.

      If NEEDS_REWRITE, list which non-compliant parts (fragments) should be discarded or
      what is missing, so a regenerated answer can be produced.

      Respond ONLY in this exact JSON shape (no surrounding text):
      {"verdict": "ACCEPT" | "NEEDS_REWRITE", "reason": "short explanation"}
      """)
  @Agent(
      name = "ResponseVerifierAgent",
      description = "Verifies assistant responses for sufficiency and accuracy before delivery",
      outputKey = "verification")
  @UserMessage(
      """
      user_question:
      {{userQuestion}}

      user_context:
      {{userContext}}

      assistant_answer:
      {{answer}}
      """)
  String verify(
      @V("userQuestion") String userQuestion,
      @V("userContext") String userContext,
      @V("answer") String answer);

  @ErrorHandler
  static ErrorRecoveryResult onError(ErrorContext ctx) {
    return ErrorRecoveryResult.result(
        "{\"verdict\": \"NEEDS_REWRITE\", \"reason\": \"verifier unavailable, answer"
            + " unverified\"}");
  }
}
