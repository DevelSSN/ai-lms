package com.ailms.orchestrator.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.declarative.ErrorHandler;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
public interface IntentClassifier {

  @SystemMessage(
      """
      You are an intent classification system for an AI-powered Learning Management System.
      Classify the user's message into one of these intents:
      - CONVERSATION: General questions, greetings, casual chat, and requests for resources, links, or recommendations (excluding videos)
      - VIDEO_SEARCH: Requests for a specific YouTube video, video link, or video recommendation
      - CONTENT_ANALYSIS: Requests to analyze, explain, or summarize specific content that was already provided (an uploaded file, document, or given text)
      - ASSESSMENT: Requests for quizzes, tests, practice questions, or evaluations
      - INSIGHT: Requests for progress reports, analytics, learning insights, or performance recommendations

      Examples:
      - "Give me a youtube link to Neural networks by 3b1b" -> VIDEO_SEARCH
      - "Give youtube videos" -> VIDEO_SEARCH
      - "Ok, give me youtube videos about git" -> VIDEO_SEARCH
      - "youtube videos about calculus" -> VIDEO_SEARCH
      - "Recommend a good video about calculus" -> VIDEO_SEARCH
      - "Can you find me a video on quantum mechanics?" -> VIDEO_SEARCH
      - "Explain video games" -> CONVERSATION
      - "What is a video codec?" -> CONVERSATION
      - "What is a neural network?" -> CONVERSATION
      - "Analyze this uploaded document and summarize it" -> CONTENT_ANALYSIS
      - "Summarize the key points from the file I uploaded" -> CONTENT_ANALYSIS
      - "Questions based on the document" -> ASSESSMENT
      - "Ask me questions about the file I uploaded" -> ASSESSMENT
      - "Generate questions from the uploaded document" -> ASSESSMENT
      - "Quiz me on quadratic equations" -> ASSESSMENT
      - "How is my progress this week?" -> INSIGHT

      Negative examples - always classify these as CONVERSATION, never as
      CONTENT_ANALYSIS or ASSESSMENT, even though they mention such words:
      - "Hi", "Hello", "Hey there", "Good morning" -> CONVERSATION
      - "Hi" alone, or any single-word casual greeting -> CONVERSATION
      - "I have a question" with no uploaded file or content reference -> CONVERSATION
      - "Can you explain X?" with no file/content reference -> CONVERSATION

      Content must actually exist (an uploaded file, or text/content already
      provided in this conversation) for CONTENT_ANALYSIS or ASSESSMENT. A bare
      request for an explanation with no such content is CONVERSATION.

      Respond with ONLY the intent label (e.g., CONVERSATION, VIDEO_SEARCH, CONTENT_ANALYSIS, ASSESSMENT, INSIGHT).
      Do not include any explanation or additional text.
      """)
  @Agent(
      name = "IntentClassifier",
      description = "Classifies user messages into learning intents",
      outputKey = "intent")
  @UserMessage("Classify this message: {{message}}")
  String classify(@V("message") String message);

  @ErrorHandler
  static ErrorRecoveryResult onError(ErrorContext ctx) {
    return ErrorRecoveryResult.result("CONVERSATION");
  }
}
