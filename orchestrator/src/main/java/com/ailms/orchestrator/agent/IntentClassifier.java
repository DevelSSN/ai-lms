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
      Classify the user message into EXACTLY one label based on the target object:

      1. CONTENT_ANALYSIS (Target: The Document)
         - Triggers: "the text", "the file", "the document", "the material", "the slide", "summarize", "explain the findings", "main point".
         - Example: "What is the main point of the text?"

      2. INSIGHT (Target: The User)
         - Triggers: "my progress", "my performance", "my gaps", "learning path", "study next", "how am I doing", "analyze my performance", "feedback".
         - Example: "Analyze my performance"

      3. ASSESSMENT (Target: User Knowledge)
         - Triggers: "quiz", "test", "questions", "evaluate me", "examine me", "test my knowledge".
         - Example: "Quiz me on Linear Algebra"

      4. VIDEO_SEARCH (Target: Video Content)
         - Triggers: "video", "YouTube", "clips", "visuals", "watch a tutorial".
         - Example: "Any clips explaining Microeconomics?"

      5. CONVERSATION (Target: General)
         - Triggers: Greetings, casual chat, general topic questions, or if no other trigger fits.
         - Example: "Hi there", "What is Quantum Physics?"

      CONTRAST RULES:
      - "Analyze THE TEXT" -> CONTENT_ANALYSIS
      - "Analyze MY PERFORMANCE" -> INSIGHT
      - "Explain THE MATERIAL" -> CONTENT_ANALYSIS
      - "Explain MY GAPS" -> INSIGHT

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
