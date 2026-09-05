package com.ailms.gateway.resource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.ailms.common.dto.ChatRequest;
import com.ailms.common.dto.ChatResponse;
import com.ailms.gateway.service.OrchestratorClient;
import com.ailms.gateway.service.SseBroadcastService;
import io.smallrye.mutiny.Multi;
import jakarta.ws.rs.core.Response;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@ExtendWith(MockitoExtension.class)
class ResourceUnitTest {

  @Mock JsonWebToken jwt;
  @Mock OrchestratorClient orchestrator;
  @Mock S3Client s3;
  @Mock ContentResource.ContentDocumentRepository contentDocRepo;

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
    when(jwt.getSubject()).thenReturn("user-1");
    InteractResource resource = new InteractResource();
    resource.jwt = jwt;
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

  @Test
  void contentResource_upload_rejectsOversizedFile(@TempDir Path tempDir) throws Exception {
    when(jwt.getSubject()).thenReturn("user-1");
    Path file = tempDir.resolve("huge.txt");
    Files.writeString(file, "x".repeat(11));

    FileUpload upload = mock(FileUpload.class);
    when(upload.fileName()).thenReturn("huge.txt");
    when(upload.uploadedFile()).thenReturn(file);

    ContentResource resource = new ContentResource();
    resource.jwt = jwt;
    resource.s3 = s3;
    resource.contentDocRepo = contentDocRepo;
    resource.maxUploadSize = 10;

    Response resp = resource.uploadFile(upload, "t-1");

    assertEquals(Response.Status.REQUEST_ENTITY_TOO_LARGE.getStatusCode(), resp.getStatus());
    verify(s3, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
  }

  @Test
  void contentResource_upload_cleansUpOrphanedBlobOnDbFailure(@TempDir Path tempDir)
      throws Exception {
    when(jwt.getSubject()).thenReturn("user-1");
    Path file = tempDir.resolve("notes.txt");
    Files.writeString(file, "hello");

    FileUpload upload = mock(FileUpload.class);
    when(upload.fileName()).thenReturn("notes.txt");
    when(upload.contentType()).thenReturn("text/plain");
    when(upload.uploadedFile()).thenReturn(file);
    doThrow(new RuntimeException("db down")).when(contentDocRepo).save(any());

    ContentResource resource = new ContentResource();
    resource.jwt = jwt;
    resource.s3 = s3;
    resource.contentDocRepo = contentDocRepo;
    resource.maxUploadSize = 10_000_000;

    Response resp = resource.uploadFile(upload, "t-1");

    assertEquals(502, resp.getStatus());
    verify(s3).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    verify(s3).deleteObject(any(DeleteObjectRequest.class));
  }

  @Test
  void sanitizeFileName_stripsPathSeparatorsAndControls() {
    assertEquals("name.txt", ContentResource.sanitizeFileName("name.txt"));
    assertEquals("a_b_c.txt", ContentResource.sanitizeFileName("a/b\\c.txt"));
    assertEquals("no_ control", ContentResource.sanitizeFileName("no\u0000 control"));
    assertEquals("upload.bin", ContentResource.sanitizeFileName("   "));
  }
}
