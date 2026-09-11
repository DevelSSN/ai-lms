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

      IMPORTANT: the assistant_answer is whatever the assistant produced for the student
      (a summary, an explanation, or a structured quiz). It must NEVER contain a verdict,
      self-assessment, JSON metadata, or any evaluation of its own sufficiency. Do NOT
      reject an answer for lacking such a self-evaluation. The only JSON you ever produce
      is your own report at the end of this message.

      Decide ACCEPT or NEEDS_REWRITE:
      - ACCEPT if the answer is on-topic, factually consistent with the user_context, and
        meaningfully answers the user_question. Structured answers (e.g. a quiz with a
        list of questions, or a summary broken into sections) are fine and should be
        accepted when they are grounded in the context.
      - NEEDS_REWRITE only if the answer is off-topic, empty, hallucinates facts NOT
        supported by the context, merely repeats the question without answering, or
        ignores a clear requirement of the user_question.

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
