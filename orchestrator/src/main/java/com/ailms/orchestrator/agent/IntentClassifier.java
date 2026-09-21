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
      You are a routing bot. Map the message to EXACTLY one label.
      Check in this order:

      1. VIDEO_SEARCH: If "video", "YouTube", "clip", "watch", "tutorial", "visual" is mentioned.
      2. CONTENT_ANALYSIS: If "the text", "the file", "the document", "the material", "summarize", "main point", "analysis of the" is mentioned.
      3. ASSESSMENT: If "quiz", "test", "question", "evaluate", "examine" is mentioned.
      4. INSIGHT: If "progress", "performance", "gap", "learning path", "study next", "how am I doing", "feedback" is mentioned.
      5. CONVERSATION: Everything else.

      EXAMPLES:
      "I want a video on Calculus" -> VIDEO_SEARCH
      "Any clips explaining X?" -> VIDEO_SEARCH
      "What is the main point of the text?" -> CONTENT_ANALYSIS
      "I need an analysis of the text" -> CONTENT_ANALYSIS
      "Quiz me on X" -> ASSESSMENT
      "Test my knowledge" -> ASSESSMENT
      "What is my progress?" -> INSIGHT
      "Analyze my performance" -> INSIGHT
      "Provide feedback on my path" -> INSIGHT
      "Hi" -> CONVERSATION

      Respond ONLY with the label. No thinking. No explanation.
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
