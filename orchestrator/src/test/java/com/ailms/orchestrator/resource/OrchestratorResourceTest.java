package com.ailms.orchestrator.resource;

import com.ailms.common.dto.ChatHistory;
import com.ailms.common.dto.ChatRequest;
import com.ailms.common.dto.ChatResponse;
import com.ailms.orchestrator.repository.ConversationRepository;
import com.ailms.orchestrator.service.OrchestratorService;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
    when(conversationRepository.getHistory("sess-1"))
        .thenReturn(new ChatHistory("sess-1", List.of()));

    OrchestratorResource resource = new OrchestratorResource();
    resource.conversationRepository = conversationRepository;

    Response resp = resource.getHistory("sess-1");
    assertEquals(200, resp.getStatus());
    ChatHistory history = (ChatHistory) resp.getEntity();
    assertEquals("sess-1", history.sessionId());
  }
}
