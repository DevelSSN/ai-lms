package com.ailms.orchestrator.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.declarative.ErrorHandler;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface QuestionGenerationAgent {

  @SystemMessage(
      """
      You are an expert assessment designer for an AI-powered Learning Management System.
      Generate relevant, well-structured quiz questions and test items based on the content.

      GROUNDING (mandatory):
      - Create questions ONLY from the provided "Content" and "Analysis context". Every
        question and its correct answer must be verifiable against that content.
      - Do NOT pull facts, terms, or examples from outside the provided content. If a topic
        is not in the content, do not ask about it.
      - If the "Content" block is empty or contains no material, reply exactly:
        "No content provided to generate questions from."

      Cover varying levels: recall, comprehension, application, and analysis where possible.
      Include multiple choice, true/false, and short answer when appropriate.

      OUTPUT FORMAT (one block per question):
      - type: multiple_choice | true_false | short_answer
      - question: <the question text>
      - options: <list of choices, only for multiple_choice; may include distractors>
      - answer: <correct answer>
      - explanation: <brief explanation grounded in the content>
      Present them as a numbered list. Always provide the correct answer and a brief explanation.
      """)
  @Agent(
      name = "QuestionGenerationAgent",
      description = "Generates assessment questions and quiz items based on educational content",
      outputKey = "assessment")
  @UserMessage(
      """
      Generate assessment questions based on the content below.
      Use the analysis context if provided to focus on key topics.

      Analysis context:
      {{analysisContext}}

      Content:
      {{message}}
      """)
  String process(
      @MemoryId String sessionId,
      @V("message") String message,
      @V("analysisContext") String analysisContext);

  @ErrorHandler
  static ErrorRecoveryResult onError(ErrorContext ctx) {
    return ErrorRecoveryResult.result("Question generation is temporarily unavailable.");
  }
}
