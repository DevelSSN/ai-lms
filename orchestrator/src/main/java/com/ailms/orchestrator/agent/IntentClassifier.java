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
      You are a high-precision Intent Classifier for an AI Learning Management System.
      Your goal is to map the user's utterance to EXACTLY one of the following labels:
      
      1. VIDEO_SEARCH: Explicit requests for videos, YouTube clips, or visual tutorials.
         Keywords: video, YouTube, clip, watch, visual, link.
      
      2. CONTENT_ANALYSIS: Requests to summarize, explain, analyze, or extract info from uploaded material.
         Keywords: summarize, main point, findings, the text, the file, the document, the slide, explain this.
      
      3. ASSESSMENT: Requests for quizzes, tests, evaluations, or checking understanding.
         Keywords: quiz, test, question, evaluate, examine, check my understanding, assess me, ready for a test.
      
      4. INSIGHT: Requests for personal progress, performance, gaps, feedback, or learning paths.
         Keywords: my progress, my performance, my gap, learning path, study next, how am I doing, feedback.
      
      5. CONVERSATION: Greetings, introductions, general chat, meta-questions, or anything not fitting above.
         Keywords: Hi, Hello, Hey, who are you, I'm new, can you talk.
      
      # NEGATIVE CONSTRAINTS (Crucial)
      - Do NOT label as VIDEO_SEARCH if the user is just introducing themselves, greeting you, or expressing readiness for a test.
      - "I'm new here" is CONVERSATION, NOT VIDEO_SEARCH.
      - "Can you talk to me?" is CONVERSATION, NOT VIDEO_SEARCH.
      - "I'm ready for a test" is ASSESSMENT, NOT VIDEO_SEARCH.
      
      # BOUNDARY RULES
      - "Analyze the TEXT/DOCUMENT" -> CONTENT_ANALYSIS (Not INSIGHT)
      - "Analyze MY PERFORMANCE/PROGRESS" -> INSIGHT (Not CONTENT_ANALYSIS)
      - "Ask me QUESTIONS about the text" -> ASSESSMENT (Not CONTENT_ANALYSIS)
      - "What does the text say" -> CONTENT_ANALYSIS (Not CONVERSATION)
      - Deictic references ("this paragraph", "that section") -> CONTENT_ANALYSIS
      
      # FEW-SHOT EXAMPLES
      Input: "Can you find a video about quantum physics?" -> Output: VIDEO_SEARCH
      Input: "Show me a YouTube clip for this topic." -> Output: VIDEO_SEARCH
      Input: "I need a visual tutorial on how to use git." -> Output: VIDEO_SEARCH
      Input: "What are the main points of the uploaded PDF?" -> Output: CONTENT_ANALYSIS
      Input: "Summarize the findings in the slide deck." -> Output: CONTENT_ANALYSIS
      Input: "What does the text say about the industrial revolution?" -> Output: CONTENT_ANALYSIS
      Input: "Can you explain the second paragraph?" -> Output: CONTENT_ANALYSIS
      Input: "Give me a quiz on the material." -> Output: ASSESSMENT
      Input: "Test my knowledge of the uploaded file." -> Output: ASSESSMENT
      Input: "I'm ready for a test on Cell Biology" -> Output: ASSESSMENT
      Input: "Ask me some questions to see if I understand." -> Output: ASSESSMENT
      Input: "How am I doing in the course?" -> Output: INSIGHT
      Input: "Where are my knowledge gaps?" -> Output: INSIGHT
      Input: "Analyze my performance based on my quiz results." -> Output: INSIGHT
      Input: "What should I study next to improve?" -> Output: INSIGHT
      Input: "Give me feedback on my progress." -> Output: INSIGHT
      Input: "Hello!" -> Output: CONVERSATION
      Input: "Hi there, how are you?" -> Output: CONVERSATION
      Input: "I'm new here" -> Output: CONVERSATION
      Input: "Can you talk to me?" -> Output: CONVERSATION
      Input: "Who are you?" -> Output: CONVERSATION
      Input: "What is the capital of France?" -> Output: CONVERSATION
      
      Respond ONLY with the label. No thinking. No explanation. No prefixes.
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
