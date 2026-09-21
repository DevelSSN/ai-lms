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
      Assign the user's message to exactly one intent. Output the intent label and nothing else.

      === INTENTS ===
      CONTENT_ANALYSIS: the message is about uploaded study material (text, file, document,
      slide, PDF, paper, chapter, article). Summarizing, reviewing, breaking down, or asking
      what it says are all this intent.

      ASSESSMENT: the user wants to be tested — a quiz, test, questions, or a check of their
      understanding of a topic.

      VIDEO_SEARCH: the user asks for a video, clip, visual guide, or tutorial on a topic.

      INSIGHT: the user asks about their own learning — progress, performance, knowledge gaps,
      what to study next, or a report on how they are doing.

      CONVERSATION: greetings, small talk, questions about the assistant, or chat with no task.

      === RULE ===
      Pick the intent whose meaning fits best. Match on meaning: different wording that means
      the same thing gets the same intent.

      === EXAMPLES ===
      "Summarize the findings in the material"    -> CONTENT_ANALYSIS
      "What is the main point of the text?"        -> CONTENT_ANALYSIS
      "What does the document say about X?"         -> CONTENT_ANALYSIS
      "Quiz me on Linear Algebra"                   -> ASSESSMENT
      "Give me some questions on Psychology"        -> ASSESSMENT
      "Check if I understood Microeconomics"        -> ASSESSMENT
      "Find a video on Calculus"                    -> VIDEO_SEARCH
      "Any clips explaining Machine Learning?"      -> VIDEO_SEARCH
      "I need a visual guide on Algorithms"         -> VIDEO_SEARCH
      "How am I doing in the course?"               -> INSIGHT
      "What are my knowledge gaps?"                 -> INSIGHT
      "Show my learning report"                     -> INSIGHT
      "I'm new here"                                -> CONVERSATION
      "What's up?"                                  -> CONVERSATION
      "Can you talk to me?"                         -> CONVERSATION

      Label:
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
