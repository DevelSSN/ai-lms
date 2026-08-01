package com.ailms.orchestrator.resource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ailms.common.dto.ChatHistory;
import com.ailms.common.dto.ChatRequest;
import com.ailms.common.dto.ChatResponse;
import com.ailms.common.dto.ThreadRenameRequest;
import com.ailms.common.dto.ThreadSummary;
import com.ailms.orchestrator.repository.ConversationRepository;
import com.ailms.orchestrator.service.OrchestratorService;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrchestratorResourceTest {

  @Mock OrchestratorService orchestratorService;
  @Mock ConversationRepository conversationRepository;

  @Test
  void processMessage() {
    when(orchestratorService.route(any(ChatRequest.class), eq("user-1")))
        .thenReturn(new ChatResponse("Hello!", "sess-1", "CONVERSATION"));

    OrchestratorResource resource = new OrchestratorResource();
    resource.orchestratorService = orchestratorService;

    Response resp = resource.processMessage(new ChatRequest("hi", "sess-1"), "user-1");
    assertEquals(200, resp.getStatus());
    ChatResponse body = (ChatResponse) resp.getEntity();
    assertEquals("Hello!", body.message());
  }

  @Test
  void getHistory() {
    when(conversationRepository.getHistory("user-1", "sess-1"))
        .thenReturn(new ChatHistory("sess-1", List.of()));

    OrchestratorResource resource = new OrchestratorResource();
    resource.conversationRepository = conversationRepository;

    Response resp = resource.getHistory("sess-1", "user-1");
    assertEquals(200, resp.getStatus());
    ChatHistory history = (ChatHistory) resp.getEntity();
    assertEquals("sess-1", history.sessionId());
  }

  @Test
  void getThreads() {
    ThreadSummary summary =
        new ThreadSummary("sess-1", "Neural Networks Explained", Instant.now(), 4);
    when(conversationRepository.listThreads("user-1")).thenReturn(List.of(summary));

    OrchestratorResource resource = new OrchestratorResource();
    resource.conversationRepository = conversationRepository;

    Response resp = resource.getThreads("user-1");
    assertEquals(200, resp.getStatus());
    List<ThreadSummary> threads = (List<ThreadSummary>) resp.getEntity();
    assertEquals(1, threads.size());
    assertEquals("sess-1", threads.get(0).sessionId());
    assertEquals("Neural Networks Explained", threads.get(0).title());
  }

  @Test
  void renameThread() {
    OrchestratorResource resource = new OrchestratorResource();
    resource.conversationRepository = conversationRepository;

    Response resp = resource.renameThread("sess-1", new ThreadRenameRequest("New Title"), "user-1");
    assertEquals(204, resp.getStatus());
    verify(conversationRepository).renameThread("user-1", "sess-1", "New Title");
  }

  @Test
  void deleteThread() {
    OrchestratorResource resource = new OrchestratorResource();
    resource.conversationRepository = conversationRepository;

    Response resp = resource.deleteThread("sess-1", "user-1");
    assertEquals(204, resp.getStatus());
    verify(conversationRepository).deleteThread("user-1", "sess-1");
  }
}
