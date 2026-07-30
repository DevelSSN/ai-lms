package com.ailms.gateway.resource;

import com.ailms.common.dto.AssessmentRequest;
import com.ailms.common.dto.ChatRequest;
import com.ailms.common.dto.ChatResponse;
import com.ailms.common.entity.ContentDocument;
import com.ailms.gateway.service.OrchestratorClient;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.file.Files;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Path("/api/v1/content")
@Tag(name = "Content", description = "Content analysis and assessment endpoints")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"student", "teacher", "admin"})
public class ContentResource {

  @Inject @RestClient OrchestratorClient orchestrator;

  @Inject JsonWebToken jwt;

  @Inject S3Client s3;

  @Inject ContentDocumentRepository contentDocRepo;

  static final String BUCKET = "ailms-content";

  @POST
  @Path("/upload")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Transactional
  public Response uploadFile(@RestForm("file") FileUpload file) {
    String userId = jwt.getSubject();
    log.info("File upload from user={} fileName={}", userId, file.fileName());
    try {
      String storagePath = "uploads/" + userId + "/" + UUID.randomUUID() + "_" + file.fileName();
      byte[] fileBytes = Files.readAllBytes(file.uploadedFile());

      PutObjectRequest putReq = PutObjectRequest.builder()
          .bucket(BUCKET)
          .key(storagePath)
          .contentType(file.contentType())
          .build();
      s3.putObject(putReq, RequestBody.fromBytes(fileBytes));

      ContentDocument doc = new ContentDocument();
      doc.userId = userId;
      doc.fileName = file.fileName();
      doc.fileType = file.contentType();
      doc.fileSize = (long) fileBytes.length;
      doc.storagePath = storagePath;
      doc.uploadedAt = Instant.now();
      doc.status = "UPLOADED";
      contentDocRepo.persist(doc);

      ChatRequest request = new ChatRequest(
          "Analyze the uploaded file: " + doc.id, "upload-" + userId);
      ChatResponse response = orchestrator.processMessage(request);
      return Response.ok(response).build();
    } catch (Exception e) {
      log.error("Upload failed for user={}: {}", userId, e.getMessage());
      return Response.status(Response.Status.BAD_GATEWAY)
          .entity(java.util.Map.of("error", "Upload failed", "detail", e.getMessage()))
          .build();
    }
  }

  @ApplicationScoped
  static class ContentDocumentRepository implements PanacheRepository<ContentDocument> {}


  @POST
  @Path("/assess")
  public Response generateAssessment(AssessmentRequest request) {
    String userId = jwt.getSubject();
    log.info("Assessment generation requested for content={} by user={}", request.contentId(), userId);
    try {
      ChatRequest chatReq = new ChatRequest(
          "Generate assessment for content " + request.contentId(), "assess-" + userId);
      ChatResponse response = orchestrator.processMessage(chatReq);
      return Response.ok(response).build();
    } catch (Exception e) {
      log.error("Orchestrator unavailable for user={}: {}", userId, e.getMessage());
      return Response.status(Response.Status.BAD_GATEWAY).build();
    }
  }

  @GET
  @Path("/insights")
  public Response getInsights() {
    String userId = jwt.getSubject();
    log.info("Insights requested by user={}", userId);
    try {
      ChatRequest request = new ChatRequest("Show my learning insights and progress", "insight-" + userId);
      ChatResponse response = orchestrator.processMessage(request);
      return Response.ok(response).build();
    } catch (Exception e) {
      log.error("Orchestrator unavailable for user={}: {}", userId, e.getMessage());
      return Response.status(Response.Status.BAD_GATEWAY).build();
    }
  }
}
