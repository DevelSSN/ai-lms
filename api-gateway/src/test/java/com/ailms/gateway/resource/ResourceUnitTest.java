package com.ailms.gateway.resource;

import com.ailms.common.dto.ChatRequest;
import com.ailms.common.dto.ChatResponse;
import com.ailms.gateway.service.OrchestratorClient;
import com.ailms.gateway.service.SseBroadcastService;
import io.smallrye.mutiny.Multi;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResourceUnitTest {

  @Mock JsonWebToken jwt;
  @Mock OrchestratorClient orchestrator;

  @Test
  void chatResource_sendMessage_success() {
    when(jwt.getSubject()).thenReturn("user-1");
    when(orchestrator.processMessage(any(ChatRequest.class)))
        .thenReturn(new ChatResponse("Hello!", "sess-1", "CONVERSATION"));

    ChatResource resource = new ChatResource();
    resource.jwt = jwt;
    resource.orchestrator = orchestrator;

    ChatRequest request = new ChatRequest("hi", "sess-1");
    Response resp = resource.sendMessage(request);
    assertEquals(200, resp.getStatus());
    ChatResponse body = (ChatResponse) resp.getEntity();
    assertEquals("Hello!", body.message());
  }

  @Test
  void chatResource_sendMessage_orchestratorDown() {
    when(jwt.getSubject()).thenReturn("user-1");
    when(orchestrator.processMessage(any(ChatRequest.class)))
        .thenThrow(new RuntimeException("Connection refused"));

    ChatResource resource = new ChatResource();
    resource.jwt = jwt;
    resource.orchestrator = orchestrator;

    Response resp = resource.sendMessage(new ChatRequest("hi", "sess-1"));
    assertEquals(500, resp.getStatus());
  }

  @Test
  void chatResource_getHistory_success() {
    when(orchestrator.getHistory("sess-1"))
        .thenReturn(new com.ailms.common.dto.ChatHistory("sess-1", java.util.List.of()));

    ChatResource resource = new ChatResource();
    resource.orchestrator = orchestrator;

    Response resp = resource.getHistory("sess-1");
    assertEquals(200, resp.getStatus());
  }

  @Test
  void profileResource_getProfile_success() {
    when(jwt.getSubject()).thenReturn("user-1");
    when(orchestrator.processMessage(any(ChatRequest.class)))
        .thenReturn(new ChatResponse("Profile data", "profile-user-1", "PROFILE"));

    ProfileResource resource = new ProfileResource();
    resource.jwt = jwt;
    resource.orchestrator = orchestrator;

    Response resp = resource.getProfile();
    assertEquals(200, resp.getStatus());
    ChatResponse body = (ChatResponse) resp.getEntity();
    assertEquals("Profile data", body.message());
  }

  @Test
  void profileResource_updateProfile_success() {
    when(jwt.getSubject()).thenReturn("user-1");
    when(orchestrator.processMessage(any(ChatRequest.class)))
        .thenReturn(new ChatResponse("Updated", "profile-user-1", "PROFILE"));

    ProfileResource resource = new ProfileResource();
    resource.jwt = jwt;
    resource.orchestrator = orchestrator;

    Response resp = resource.updateProfile(Map.of("name", "New Name"));
    assertEquals(200, resp.getStatus());
  }

  @Test
  void interactResource_interact_success() {
    when(jwt.getSubject()).thenReturn("user-1");
    when(orchestrator.processMessage(any(ChatRequest.class)))
        .thenReturn(new ChatResponse("Response", "thread-1", "CONVERSATION"));

    InteractResource resource = new InteractResource();
    resource.jwt = jwt;
    resource.orchestrator = orchestrator;

    Response resp = resource.interact(Map.of("message", "hello", "thread_id", "thread-1"));
    assertEquals(200, resp.getStatus());
    ChatResponse body = (ChatResponse) resp.getEntity();
    assertEquals("Response", body.message());
  }

  @Test
  void interactResource_orchestratorDown_returns502() {
    when(jwt.getSubject()).thenReturn("user-1");
    when(orchestrator.processMessage(any(ChatRequest.class)))
        .thenThrow(new RuntimeException("Down"));

    InteractResource resource = new InteractResource();
    resource.jwt = jwt;
    resource.orchestrator = orchestrator;

    Response resp = resource.interact(Map.of("message", "hello", "thread_id", "t-1"));
    assertEquals(502, resp.getStatus());
  }

  @Test
  void interactResource_updates_returnsMulti() {
    InteractResource resource = new InteractResource();
    resource.sse = new SseBroadcastService();

    Multi<String> stream = resource.stream();
    assertNotNull(stream);
  }

  @Test
  void contentResource_insights_success() {
    when(jwt.getSubject()).thenReturn("user-1");
    when(orchestrator.processMessage(any(ChatRequest.class)))
        .thenReturn(new ChatResponse("Great progress", "insight-user-1", "INSIGHT"));

    ContentResource resource = new ContentResource();
    resource.jwt = jwt;
    resource.orchestrator = orchestrator;

    Response resp = resource.getInsights();
    assertEquals(200, resp.getStatus());
    ChatResponse body = (ChatResponse) resp.getEntity();
    assertEquals("Great progress", body.message());
  }

  @Test
  void contentResource_assess_success() {
    when(jwt.getSubject()).thenReturn("user-1");
    when(orchestrator.processMessage(any(ChatRequest.class)))
        .thenReturn(new ChatResponse("Assessment ready", "assess-user-1", "ASSESSMENT"));

    ContentResource resource = new ContentResource();
    resource.jwt = jwt;
    resource.orchestrator = orchestrator;

    var req = new com.ailms.common.dto.AssessmentRequest("content-1", "user-1", 5, "medium");
    Response resp = resource.generateAssessment(req);
    assertEquals(200, resp.getStatus());
  }
}
