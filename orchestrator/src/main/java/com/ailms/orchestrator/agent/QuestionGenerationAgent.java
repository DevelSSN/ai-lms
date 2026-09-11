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

@RegisterAiService(modelName = "quiz")
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

      Generate EXACTLY {{questionCount}} questions (default 5 if unspecified).
      Tailor the cognitive level to the requested difficulty:
      - easy: recall and recognition; straightforward options
      - medium: comprehension and basic application
      - hard: application, analysis, and synthesis; multiple plausible distractors

      Cover varying levels: recall, comprehension, application, and analysis where possible.
      Include multiple choice, true/false, and short answer when appropriate.

      OUTPUT FORMAT:
      Reply with a single JSON array of exactly {{questionCount}} objects. No extra text, no
      markdown fences. The JSON object schema per question is:
      {
        "question": "<the question text>",
        "type": "multiple_choice | true_false | short_answer",
        "options": ["<option A>", "<option B>", "<option C>"],  // only for multiple_choice, omit otherwise
        "answer": "<correct answer>",
        "explanation": "<brief explanation grounded in the content>"
      }
      """)
  @Agent(
      name = "QuestionGenerationAgent",
      description = "Generates assessment questions and quiz items based on educational content",
      outputKey = "assessment")
  @UserMessage(
      """
      Generate {{questionCount}} assessment questions at {{difficulty}} difficulty
      based on the content below. Use the analysis context if provided to focus on
      key topics. Respond with ONLY the JSON array described in the system prompt.

      Difficulty: {{difficulty}}
      Question count: {{questionCount}}

      Analysis context:
      {{analysisContext}}

      Content:
      {{message}}
      """)
  String process(
      @MemoryId String sessionId,
      @V("message") String message,
      @V("analysisContext") String analysisContext,
      @V("difficulty") String difficulty,
      @V("questionCount") Integer questionCount);

  @ErrorHandler
  static ErrorRecoveryResult onError(ErrorContext ctx) {
    return ErrorRecoveryResult.result("Question generation is temporarily unavailable.");
  }
}
