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
      Classify the user message into EXACTLY one label. 
      
      If the message contains ANY of these keywords, you MUST use the corresponding label:
      - VIDEO_SEARCH: "video", "YouTube", "clip", "watch", "tutorial", "visual"
      - CONTENT_ANALYSIS: "the text", "the file", "the document", "the material", "summarize", "main point"
      - ASSESSMENT: "quiz", "test", "question", "evaluate", "examine"
      - INSIGHT: "my progress", "my performance", "my gap", "learning path", "study next", "how am I doing"
      - CONVERSATION: everything else (Greetings, "Hi", "Hello", general topic questions)

      EXAMPLES:
      "What is my progress?" -> INSIGHT
      "Quiz me on Linear Algebra" -> ASSESSMENT
      "Main point of the text" -> CONTENT_ANALYSIS
      "Any clips explaining X?" -> VIDEO_SEARCH
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
