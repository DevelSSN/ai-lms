package com.ailms.orchestrator.service;

import com.ailms.common.entity.ContentDocument;
import io.quarkus.hibernate.orm.panache.Panache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Slf4j
@ApplicationScoped
public class ContentDocumentService {

  @Inject ObjectStorageService objectStorage;

  @Inject DocumentParserService documentParser;

  @Transactional
  public String resolveContent(String docId) {
    try {
      ContentDocument doc = Panache.getEntityManager().find(ContentDocument.class, docId);
      if (doc == null || doc.storagePath == null) return null;
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
          log.info("Parsed document docId={} type={} textLength={}", docId, doc.fileType, content.length());
        } else {
          content = new String(fileBytes, StandardCharsets.UTF_8);
          log.warn("Parse failed for docId={}: {}, falling back to raw bytes", docId, result.error());
        }
      } else {
        content = new String(fileBytes, StandardCharsets.UTF_8);
        log.info("Unsupported type={} for docId={}, using raw bytes", doc.fileType, docId);
      }

      return "File: " + doc.fileName + "\n\nContent:\n" + content;
    } catch (Exception e) {
      log.warn("Failed to resolve file content for docId={}: {}", docId, e.getMessage());
      return null;
    }
  }
}
