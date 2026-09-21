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

      CONTENT_ANALYSIS: the user points at material already in the system — the text,
      the file, the document, the slide, the PDF, the paper, the chapter, the article,
      the material, "this", "uploaded" — and asks you to summarize, explain, review,
      break down, or extract from it.
          "Summarize the document"                      -> CONTENT_ANALYSIS
          "What does the material say about Calculus?"  -> CONTENT_ANALYSIS
          "Review the slide for me"                     -> CONTENT_ANALYSIS
          "What is discussed in the chapter?"           -> CONTENT_ANALYSIS
          "Give me a summary of the paper"              -> CONTENT_ANALYSIS
          "Break down the key concepts in the text"     -> CONTENT_ANALYSIS
          "What does the paper say about Cell Biology?" -> CONTENT_ANALYSIS
          "Does the slide mention Psychology?"          -> CONTENT_ANALYSIS
          "What are the main takeaways?"                -> CONTENT_ANALYSIS

      ASSESSMENT: the user wants to answer questions now to test what they learned —
      a quiz, a test, practice questions, or checking their understanding or readiness.
          "Quiz me on Linear Algebra"                   -> ASSESSMENT
          "Give me some questions on Psychology"        -> ASSESSMENT
          "I'm ready for a test on Cell Biology"        -> ASSESSMENT
          "Check if I understood Microeconomics"        -> ASSESSMENT
          "Evaluate my knowledge of Ethics"             -> ASSESSMENT
          "Can I have a practice test for Calculus?"    -> ASSESSMENT
          "Question me on World History"                -> ASSESSMENT
          "Evaluate my understanding of World History"  -> ASSESSMENT

      VIDEO_SEARCH: the user asks you to find external video content — a video, a clip,
      a tutorial, or a visual guide on a topic.
          "Find a video on Calculus"                    -> VIDEO_SEARCH
          "Show me a YouTube video for Microeconomics"  -> VIDEO_SEARCH
          "I need a visual guide on Algorithms"         -> VIDEO_SEARCH
          "Any clips explaining Machine Learning?"      -> VIDEO_SEARCH
          "Get me a video tutorial for Software Engineering" -> VIDEO_SEARCH
          "I want to watch a video on Computer Architecture"  -> VIDEO_SEARCH
          "Look for educational videos about World History"   -> VIDEO_SEARCH
          "Suggest a video for Database Systems"        -> VIDEO_SEARCH
          "Search for a clip about Psychology"          -> VIDEO_SEARCH
          "Recommend a YouTube video about Data Structures" -> VIDEO_SEARCH
          "Find some videos about Ethics"               -> VIDEO_SEARCH
          "Do you have a video about Artificial Intelligence?" -> VIDEO_SEARCH

      INSIGHT: the user asks about their own learning state — progress, performance,
      knowledge gaps, what to study next, or a report and feedback on how they are doing.
          "What is my progress?"                        -> INSIGHT
          "Where are my knowledge gaps?"                -> INSIGHT
          "Show my learning report"                     -> INSIGHT
          "What topics do I need to review?"            -> INSIGHT
          "Analyze my performance"                      -> INSIGHT
          "Tell me what I should study next"            -> INSIGHT
          "How am I doing in Discrete Math?"            -> INSIGHT
          "Am I good at Quantum Physics?"               -> INSIGHT
          "Where do I stand in Linear Algebra?"         -> INSIGHT
          "What's my level in Data Structures?"         -> INSIGHT

      CONVERSATION: the message has no learning task — a greeting, small talk, or a
      question about the assistant itself.
          "Hi"                                          -> CONVERSATION
          "What can you do?"                            -> CONVERSATION
          "I'm new here"                                -> CONVERSATION

      RULES:
      - If the message points at the uploaded material, it is CONTENT_ANALYSIS, even if
        it is phrased as a question or a request.
      - Asking what the material, file, document, or slide says about a topic is
        CONTENT_ANALYSIS.
      - A request for a test, a video, or info about their learning is that intent.
        Never use CONVERSATION for these.
      - Use CONVERSATION only for greetings, small talk, or meta questions about the
        assistant — never as the general default.
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
