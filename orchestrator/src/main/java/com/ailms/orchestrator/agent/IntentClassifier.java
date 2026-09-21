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
      The user is a learner talking to a tutoring assistant. Choose exactly one intent
      for their message and output only the intent label.

      CONTENT_ANALYSIS — the user points at material already in the system (the text,
      the file, the document, the slide, the PDF, the paper, the chapter, the article,
      the notes, "this", "uploaded") and asks you to explain, summarize, review, break
      down, or extract from it.
        Examples:
        "Summarize the document"
        "What does the material say about X?"
        "Review the slide for me"

      ASSESSMENT — the user wants to answer questions now to test what they learned:
      a quiz, a test, practice questions, or checking their understanding and readiness.
        Examples:
        "Quiz me on this topic"
        "Give me some questions on X"
        "I'm ready for a test on X"

      VIDEO_SEARCH — the user asks you to find external video content: a video, clip,
      tutorial, or visual guide on a topic.
        Examples:
        "Find a video on X"
        "Show me a YouTube video on X"
        "I need a visual guide on X"

      INSIGHT — the user asks about their own learning state: progress, performance,
      knowledge gaps, what to study next, or a report and feedback on how they are doing.
        Examples:
        "What is my progress?"
        "Where are my knowledge gaps?"
        "Show my learning report"

      CONVERSATION — the message has no learning task: a greeting, small talk, or a
      question about the assistant itself.
        Examples:
        "Hi"
        "What can you do?"

      RULES:
      - Whenever the message points at the uploaded material, it is CONTENT_ANALYSIS.
      - A request for a test, a video, or info about their learning is that intent —
        never fall back to CONVERSATION for it.
      - Use CONVERSATION only for greetings, small talk, or meta questions about the
        assistant, never as the general default.
      - Pick the single intent that fits best.
      Output only the label.
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
