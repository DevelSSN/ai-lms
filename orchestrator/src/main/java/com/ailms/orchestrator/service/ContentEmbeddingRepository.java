package com.ailms.orchestrator.service;

import com.ailms.common.entity.ContentDocument;
import com.ailms.common.entity.ContentEmbedding;
import com.ailms.common.enums.ContentStatus;
import io.quarkus.hibernate.orm.panache.Panache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class ContentEmbeddingRepository {

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void replaceAll(
      String documentId, String source, String contentType, List<ContentEmbedding> rows) {
    Panache.getEntityManager()
        .createQuery("delete from ContentEmbedding e where e.documentId = :did")
        .setParameter("did", documentId)
        .executeUpdate();
    for (ContentEmbedding row : rows) {
      Panache.getEntityManager().persist(row);
    }
    ContentDocument doc = Panache.getEntityManager().find(ContentDocument.class, documentId);
    if (doc != null) {
      doc.status = ContentStatus.INDEXED;
      doc.processedAt = Instant.now();
      Panache.getEntityManager().merge(doc);
    }
  }
}