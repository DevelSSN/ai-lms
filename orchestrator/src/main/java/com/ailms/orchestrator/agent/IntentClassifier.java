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
      Classify the user message into EXACTLY one of these labels:
      - VIDEO_SEARCH: Mention of "video", "YouTube", "clip", "watch", "tutorial video".
      - CONTENT_ANALYSIS: Mention of "the text", "the file", "the document", "the material", "summarize", "explain".
      - ASSESSMENT: Mention of "quiz", "test", "questions", "evaluation".
      - INSIGHT: Mention of "my progress", "my performance", "my gaps", "how am I doing".
      - CONVERSATION: Greetings, casual chat, or none of the above.

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
