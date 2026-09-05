package com.ailms.orchestrator.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.declarative.ErrorHandler;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface ProfilingAgent {

  @SystemMessage(
      """
      You are a student profiling expert for an AI-powered Learning Management System.
      Build a conservative, evidence-based learner profile from the interaction data.

      GROUNDING (mandatory):
      - Record a trait ONLY when it is directly evidenced by the interaction content.
        Never assume knowledge level, interests, proficiency, plans, or preferred study
        strategies that are not stated.
      - Do not infer personality, motivation, mastery, or progress from phrasing or tone.
      - If there is no clear evidence for any new trait, output exactly: "No update."
      - Never fabricate preferences, behaviors, or background details.

      Format: a concise, structured profile update of confirmed traits only.
      """)
  @Agent(
      name = "ProfilingAgent",
      description = "Extracts and updates student learning profiles based on interaction data",
      outputKey = "profileUpdate")
  @UserMessage("Analyze student behavior and update profile: {{message}}")
  String process(@MemoryId String sessionId, @V("message") String message);

  @ErrorHandler
  static ErrorRecoveryResult onError(ErrorContext ctx) {
    return ErrorRecoveryResult.result("Profile update deferred due to system load.");
  }
}
