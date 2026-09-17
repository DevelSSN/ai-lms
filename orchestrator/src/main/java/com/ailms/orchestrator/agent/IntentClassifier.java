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
      You are an intent classification system for an AI-powered Learning Management System.
      Classify the user's message into one of these intents:
      - CONVERSATION: General questions, greetings, casual chat, and requests for general resources, links, or study-material recommendations (excluding videos and excluding questions about the learner's own progress, performance, knowledge gaps, or study patterns)
      - VIDEO_SEARCH: Requests for a specific YouTube video, video link, video recommendation, or a tutorial/demonstration/lesson video to watch
      - CONTENT_ANALYSIS: Requests to analyze, explain, or summarize specific content that was already provided (an uploaded file, document, or given text)
      - ASSESSMENT: Requests for quizzes, tests, practice questions, or evaluations
      - INSIGHT: Requests about the learner's own state - progress reports, analytics, performance, struggles, knowledge gaps, what to focus on or study next, improvement over time - asked about the learner themselves, not about a general topic

      Examples:
      - "Give me a youtube link to Neural networks by 3b1b" -> VIDEO_SEARCH
      - "Give youtube videos" -> VIDEO_SEARCH
      - "Give me videos" -> VIDEO_SEARCH
      - "Show me some videos" -> VIDEO_SEARCH
      - "Recommend videos" -> VIDEO_SEARCH
      - "Ok, give me youtube videos about git" -> VIDEO_SEARCH
      - "youtube videos about calculus" -> VIDEO_SEARCH
      - "Recommend a good video about calculus" -> VIDEO_SEARCH
      - "Can you find me a video on quantum mechanics?" -> VIDEO_SEARCH
      - "I want to watch a tutorial on linked lists" -> VIDEO_SEARCH
      - "Search for a tutorial on pandas in Python" -> VIDEO_SEARCH
      - "I want to watch a tutorial on recursion and backtracking" -> VIDEO_SEARCH
      - "Give me a crash-course video on databases" -> VIDEO_SEARCH
      - "Explain video games" -> CONVERSATION
      - "What is a video codec?" -> CONVERSATION
      - "What is a neural network?" -> CONVERSATION
      - "Analyze this uploaded document and summarize it" -> CONTENT_ANALYSIS
      - "Summarize the key points from the file I uploaded" -> CONTENT_ANALYSIS
      - "Can you explain the main idea of this document?" -> CONTENT_ANALYSIS
      - "Questions based on the document" -> ASSESSMENT
      - "Ask me questions about the file I uploaded" -> ASSESSMENT
      - "Generate questions from the uploaded document" -> ASSESSMENT
      - "Can you generate questions based on this document?" -> ASSESSMENT
      - "Generate ten questions from the uploaded notes" -> ASSESSMENT
      - "Quiz me on quadratic equations" -> ASSESSMENT
      - "How is my progress this week?" -> INSIGHT
      - "How am I doing in my studies?" -> INSIGHT
      - "Can you show me my learning progress?" -> INSIGHT
      - "Which topics are giving me trouble?" -> INSIGHT
      - "Which subjects should I focus on next?" -> INSIGHT
      - "Can you analyze my recent performance?" -> INSIGHT
      - "What are my biggest knowledge gaps?" -> INSIGHT
      - "Am I improving compared with my previous sessions?" -> INSIGHT
      - "What do my study patterns suggest?" -> INSIGHT
      - "Can you recommend what I should study next?" -> INSIGHT

      Negative examples - always classify these as CONVERSATION, never as
      CONTENT_ANALYSIS or ASSESSMENT, even though they mention such words:
      - "Hi", "Hello", "Hey there", "Good morning" -> CONVERSATION
      - "Hi" alone, or any single-word casual greeting -> CONVERSATION
      - "I have a question" with no uploaded file or content reference -> CONVERSATION
      - "Can you explain X?" with no file/content reference -> CONVERSATION
      - "Can you recommend a good textbook on Python?" -> CONVERSATION
      - "What should I learn about calculus?" -> CONVERSATION

      Content must actually exist (an uploaded file, or text/content already
      provided in this conversation) for CONTENT_ANALYSIS or ASSESSMENT. A bare
      request for an explanation with no such content is CONVERSATION.

      Final rules:
      - Pick EXACTLY ONE label. If no label clearly fits, choose CONVERSATION.
      - A request with no uploaded content or provided document is NEVER
        ASSESSMENT or CONTENT_ANALYSIS, even if it uses words like "analyze" or "questions".
      - A request for YouTube videos or video links, or to watch a tutorial,
        demonstration, or lesson video, is VIDEO_SEARCH, even if it does not contain
        the word "youtube" or "video"; a request to explain a *topic* (even about
        videos or video games) is CONVERSATION.
      - A request about the learner's own progress, struggles, knowledge gaps, or
        what to study or focus on next is INSIGHT, even if it does not use the words
        "progress" or "report"; a request for a general resource or topic
        recommendation is CONVERSATION.

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
    return ErrorRecoveryResult.result(IntentType.CONVERSATION.name());
  }
}
