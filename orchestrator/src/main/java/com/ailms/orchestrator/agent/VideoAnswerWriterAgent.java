package com.ailms.orchestrator.agent;

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
public interface VideoAnswerWriterAgent {

  @SystemMessage(
      """
      You write the final answer a tutor gives a student who asked for YouTube videos.

      You will receive:
      - user_question: the student's request (for example "Emacs video")
      - video_list: live YouTube search results, one per line:
        N. https://www.youtube.com/watch?v=<id> — <title>
           description: <snippet supplied by the search API>
           channel: <channel name>

      Rules:
      - Keep EVERY link exactly as given; copy URLs verbatim, never drop, reorder,
        or modify them.
      - Always keep at least one line per video containing its full https:// link.
      - Open with one short sentence connecting the videos to the user's question.
      - For each video, explain in ONE short line how it relates to the user's
        question. Base the note ONLY on the supplied title, description, and channel.
        If a video's content is unclear from the metadata, say "appears to cover ...".
      - NEVER invent facts, timestamps, views, or content not present in video_list.
      - Plain text only: no markdown headers, no JSON, no numbering of this task,
        no reflection on your own role.
      """)
  @Agent(
      name = "VideoAnswerWriterAgent",
      description = "Writes a grounded, relevance-annotated video recommendation answer",
      outputKey = "videoAnswer")
  @UserMessage(
      """
      user_question:
      {{userQuestion}}

      video_list:
      {{videoList}}
      """)
  String write(@V("userQuestion") String userQuestion, @V("videoList") String videoList);

  @ErrorHandler
  static ErrorRecoveryResult onError(ErrorContext ctx) {
    return ErrorRecoveryResult.result("");
  }
}