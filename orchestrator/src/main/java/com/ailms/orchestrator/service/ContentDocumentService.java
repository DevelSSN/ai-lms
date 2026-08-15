package com.ailms.orchestrator.service;

import com.ailms.common.entity.ContentDocument;
import io.quarkus.hibernate.orm.panache.Panache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class ContentDocumentService {

  private static final int MAX_CONTENT_CHARS = 12000;

  @Inject ObjectStorageService objectStorage;

  @Inject DocumentParserService documentParser;

  @Transactional
  public String resolveContent(String docId) {
    String content = extractContent(docId);
    if (content == null) return null;
    ContentDocument doc = Panache.getEntityManager().find(ContentDocument.class, docId);

    if (content.length() > MAX_CONTENT_CHARS) {
      content = content.substring(0, MAX_CONTENT_CHARS) + "\n\n…[content truncated]";
    }

    return "File: " + doc.fileName + "\n\nContent:\n" + content;
  }

  @Transactional
  public String resolveFileName(String docId) {
    ContentDocument doc = Panache.getEntityManager().find(ContentDocument.class, docId);
    return doc != null && doc.fileName != null ? doc.fileName : docId;
  }

  @Transactional
  public String resolveRecentDocumentId(String userId, String sessionId) {
    try {
      if (sessionId != null && !sessionId.isBlank()) {
        @SuppressWarnings("unchecked")
        java.util.List<String> scoped =
            Panache.getEntityManager()
                .createQuery(
                    "select d.id from ContentDocument d where d.userId = :uid and d.sessionId ="
                        + " :sid order by d.uploadedAt desc",
                    String.class)
                .setParameter("uid", userId)
                .setParameter("sid", sessionId)
                .setMaxResults(1)
                .getResultList();
        if (!scoped.isEmpty()) return scoped.get(0);
      }
      @SuppressWarnings("unchecked")
      java.util.List<String> userWide =
          Panache.getEntityManager()
              .createQuery(
                  "select d.id from ContentDocument d where d.userId = :uid order by"
                      + " d.uploadedAt desc",
                  String.class)
              .setParameter("uid", userId)
              .setMaxResults(1)
              .getResultList();
      return userWide.isEmpty() ? null : userWide.get(0);
    } catch (Exception e) {
      log.warn(
          "Failed to resolve recent document for user={} session={}: {}",
          userId,
          sessionId,
          e.getMessage());
      return null;
    }
  }

  @Transactional
  public java.util.Set<String> resolveIndexedDocumentIds(String userId) {
    @SuppressWarnings("unchecked")
    java.util.List<String> ids =
        Panache.getEntityManager()
            .createQuery(
                "select d.id from ContentDocument d where d.userId = :uid and d.status ="
                    + " 'INDEXED'",
                String.class)
            .setParameter("uid", userId)
            .getResultList();
    return new java.util.HashSet<>(ids);
  }

  @Transactional
  public List<String> chunkContent(String docId, int chunkSize, int overlap) {
    String content = extractContent(docId);
    if (content == null) return List.of();
    List<String> chunks = chunkText(content, chunkSize, overlap);
    log.info(
        "Chunked document docId={} into {} chunks of ~{} chars", docId, chunks.size(), chunkSize);
    return chunks;
  }

  static List<String> chunkText(String content, int chunkSize, int overlap) {
    if (content == null || content.isBlank()) return List.of();
    if (chunkSize <= 0) throw new IllegalArgumentException("chunkSize must be positive");
    int ov = Math.min(overlap, chunkSize / 2);
    List<String> chunks = new ArrayList<>();
    int start = 0;
    int length = content.length();
    while (start < length) {
      int end = boundary(content, start, chunkSize);
      chunks.add(content.substring(start, end));
      if (end >= length) break;
      start = Math.max(end - ov, start + 1);
    }
    return chunks;
  }

  private static int boundary(String content, int start, int chunkSize) {
    int end = Math.min(start + chunkSize, content.length());
    if (end >= content.length()) return end;
    int newline = content.lastIndexOf('\n', end - 1);
    if (newline > start + chunkSize / 2) return newline + 1;
    int space = content.lastIndexOf(' ', end - 1);
    if (space > start + chunkSize / 2) return space + 1;
    return end;
  }

  private String extractContent(String docId) {
    ContentDocument doc = Panache.getEntityManager().find(ContentDocument.class, docId);
    if (doc == null || doc.storagePath == null) return null;
    if (doc.extractedText != null && "PARSED".equals(doc.status)) return doc.extractedText;

    byte[] fileBytes = objectStorage.readFile(doc.storagePath);
    if (fileBytes == null) return null;

    String content;
    if (documentParser.isSupported(doc.fileType)) {
      DocumentParserService.ParseResult result =
          documentParser.parse(fileBytes, doc.fileName, doc.fileType);
      if (result.isSuccess()) {
        content = result.text();
        doc.extractedText = content;
        doc.status = "PARSED";
        doc.processedAt = Instant.now();
        Panache.getEntityManager().merge(doc);
        log.info(
            "Parsed document docId={} type={} textLength={}",
            docId,
            doc.fileType,
            content.length());
      } else {
        content = new String(fileBytes, StandardCharsets.UTF_8);
        log.warn("Parse failed for docId={}: {}, falling back to raw bytes", docId, result.error());
      }
    } else {
      content = new String(fileBytes, StandardCharsets.UTF_8);
      log.info("Unsupported type={} for docId={}, using raw bytes", doc.fileType, docId);
    }
    return content;
  }
}
