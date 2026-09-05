package com.ailms.gateway.resource;

import com.ailms.common.constants.PromptPrefixes;
import com.ailms.common.dto.AssessmentRequest;
import com.ailms.common.dto.ChatRequest;
import com.ailms.common.dto.ChatResponse;
import com.ailms.common.entity.ContentDocument;
import com.ailms.common.enums.ContentStatus;
import com.ailms.gateway.service.OrchestratorClient;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.security.Authenticated;
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
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@Path("/api/v1/content")
@Tag(name = "Content", description = "Content analysis and assessment endpoints")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class ContentResource {

  @Inject @RestClient OrchestratorClient orchestrator;

  @Inject JsonWebToken jwt;

  @Inject S3Client s3;

  @Inject ContentDocumentRepository contentDocRepo;

  @ConfigProperty(name = "ailms.upload.max-size", defaultValue = "20971520")
  long maxUploadSize;

  static final String BUCKET = "ailms-content";

  @POST
  @Path("/upload")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public Response uploadFile(
      @RestForm("file") FileUpload file, @RestForm("threadId") String threadId) {
    String userId = jwt.getSubject();
    String fileName = sanitizeFileName(file.fileName());
    log.info("File upload from user={} fileName={} threadId={}", userId, fileName, threadId);
    try {
      byte[] fileBytes = Files.readAllBytes(file.uploadedFile());
      if (fileBytes.length > maxUploadSize) {
        log.warn("Rejected oversized upload for user={} size={} max={}", userId, fileBytes.length, maxUploadSize);
        return Response.status(Response.Status.REQUEST_ENTITY_TOO_LARGE)
            .entity(Map.of("error", "File too large", "detail", "Maximum upload size is " + maxUploadSize + " bytes"))
            .build();
      }

      String storagePath = "uploads/" + userId + "/" + UUID.randomUUID() + "_" + fileName;

      PutObjectRequest putReq =
          PutObjectRequest.builder()
              .bucket(BUCKET)
              .key(storagePath)
              .contentType(file.contentType())
              .build();
      s3.putObject(putReq, RequestBody.fromBytes(fileBytes));

      ContentDocument doc = new ContentDocument();
      try {
        doc.userId = userId;
        doc.sessionId = threadId;
        doc.fileName = fileName;
        doc.fileType = file.contentType();
        doc.fileSize = (long) fileBytes.length;
        doc.storagePath = storagePath;
        doc.status = ContentStatus.UPLOADED;
        contentDocRepo.save(doc);
      } catch (Exception e) {
        log.error("Cleaning up S3 object after DB failure for user={}: {}", userId, e.getMessage());
        deleteObjectQuietly(storagePath);
        throw e;
      }

      ChatRequest request =
          new ChatRequest(PromptPrefixes.UPLOAD_ANALYSIS + doc.id, "upload:" + doc.id);
      ChatResponse response = orchestrator.processMessage(request);
      return Response.ok(response).build();
    } catch (Exception e) {
      log.error("Upload failed for user={}: {}", userId, e.getMessage());
      return Response.status(Response.Status.BAD_GATEWAY)
          .entity(Map.of("error", "Upload failed"))
          .build();
    }
  }

  private void deleteObjectQuietly(String storagePath) {
    try {
      s3.deleteObject(DeleteObjectRequest.builder().bucket(BUCKET).key(storagePath).build());
    } catch (Exception cleanupErr) {
      log.warn("Failed to clean up orphaned S3 object {}: {}", storagePath, cleanupErr.getMessage());
    }
  }

  static String sanitizeFileName(String fileName) {
    if (fileName == null || fileName.isBlank()) return "upload.bin";
    String cleaned = fileName.replaceAll("[\\\\/\\p{Cntrl}]", "_").trim();
    if (cleaned.length() > 120) {
      cleaned = cleaned.substring(cleaned.length() - 120);
    }
    return cleaned;
  }

  @ApplicationScoped
  static class ContentDocumentRepository implements PanacheRepository<ContentDocument> {

    @Transactional
    public void save(ContentDocument doc) {
      persist(doc);
    }
  }

  @POST
  @Path("/assess")
  public Response generateAssessment(AssessmentRequest request) {
    String userId = jwt.getSubject();
    log.info(
        "Assessment generation requested for content={} by user={}", request.contentId(), userId);
    try {
      int questionCount = request.questionCount() != null && request.questionCount() > 0
          ? request.questionCount()
          : 5;
      String difficulty =
          request.difficulty() != null && !request.difficulty().isBlank()
              ? request.difficulty().trim()
              : "medium";
      ChatRequest chatReq =
          new ChatRequest(
              "Generate assessment for content "
                  + request.contentId()
                  + " | questions="
                  + questionCount
                  + " | difficulty="
                  + difficulty,
              "assess-" + userId);
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
      ChatRequest request =
          new ChatRequest("Show my learning insights and progress", "insight-" + userId);
      ChatResponse response = orchestrator.processMessage(request);
      return Response.ok(response).build();
    } catch (Exception e) {
      log.error("Orchestrator unavailable for user={}: {}", userId, e.getMessage());
      return Response.status(Response.Status.BAD_GATEWAY).build();
    }
  }
}
