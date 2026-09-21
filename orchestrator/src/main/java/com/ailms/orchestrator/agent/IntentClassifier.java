package com.ailms.orchestrator.agent;

import com.ailms.common.enums.IntentType;
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
public interface IntentClassifier {

  @SystemMessage(
      """
      Classify the user message into EXACTLY one label:

      1. VIDEO_SEARCH: Requests for "video", "YouTube", "clips", "visuals", "tutorials", "watch".
      2. CONTENT_ANALYSIS: User wants the AI to explain, summarize, or find info in "the text", "the file", "the document", "the material".
      3. ASSESSMENT: User wants the AI to quiz, test, or evaluate them. Keywords: "quiz", "test", "questions", "examine me".
      4. INSIGHT: User asks about their own "progress", "performance", "growth", "struggles", "gaps", "how am I doing", "analyze my performance".
      5. CONVERSATION: Greetings, casual chat, or any general question not fitting above.

      CONTRAST RULES:
      - "What does the text say about X?" -> CONTENT_ANALYSIS (AI explains)
      - "Ask me questions about the text" -> ASSESSMENT (AI tests)
      - "Any clips explaining X?" -> VIDEO_SEARCH
      - "Analyze my performance" -> INSIGHT

      Examples:
      "What is my progress?" -> INSIGHT
      "Quiz me on this" -> ASSESSMENT
      "Summarize the text" -> CONTENT_ANALYSIS
      "Find a video on X" -> VIDEO_SEARCH
      "Hello" -> CONVERSATION

      Respond ONLY with the label. No thinking, no explanation.
      """)
  @Agent(
      name = "IntentClassifier",
      description = "Classifies user messages into learning intents",
      outputKey = "intent")
  @UserMessage("Message: {{message}}\nLabel:")
  String classify(@V("message") String message);

  @ErrorHandler
  static ErrorRecoveryResult onError(ErrorContext ctx) {
    return ErrorRecoveryResult.result(IntentType.CONVERSATION.name());
  }
}
