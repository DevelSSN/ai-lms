package com.ailms.orchestrator.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.MemoryId;
import io.quarkiverse.langchain4j.RegisterAiService;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

@RegisterAiService
public interface QuestionGenerationAgent {

  @SystemMessage("""
      You are an expert assessment designer for an AI-powered Learning Management System.
      Generate relevant and well-structured quiz questions and test items based on educational content.
      Create questions that test understanding at various levels: recall, comprehension, application, and analysis.
      Include multiple choice, true/false, and short answer questions when appropriate.
      Always provide correct answers and brief explanations.
      """)
  @Agent(
      name = "QuestionGenerationAgent",
      description = "Generates assessment questions and quiz items based on educational content",
      outputKey = "assessment")
  @UserMessage("Generate assessment questions for the following content: {{content}}")
  String process(@MemoryId String sessionId, @V("content") String content);
}
