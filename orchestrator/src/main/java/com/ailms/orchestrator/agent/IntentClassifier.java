package com.ailms.orchestrator.agent;

import dev.langchain4j.agentic.Agent;
import io.quarkiverse.langchain4j.RegisterAiService;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

@RegisterAiService
public interface IntentClassifier {

  @SystemMessage("""
      You are an intent classification system for an AI-powered Learning Management System.
      Classify the user's message into one of these intents:
      - CONVERSATION: General questions, greetings, casual chat
      - CONTENT_ANALYSIS: Requests to analyze, explain, summarize, or review educational content
      - ASSESSMENT: Requests for quizzes, tests, practice questions, or evaluations
      - INSIGHT: Requests for progress reports, analytics, learning insights, or recommendations
      
      Respond with ONLY the intent label (e.g., CONVERSATION, CONTENT_ANALYSIS, ASSESSMENT, INSIGHT).
      Do not include any explanation or additional text.
      """)
  @Agent(
      name = "IntentClassifier",
      description = "Classifies user messages into learning intents",
      outputKey = "intent")
  @UserMessage("Classify this message: {{message}}")
  String classify(@V("message") String message);
}
