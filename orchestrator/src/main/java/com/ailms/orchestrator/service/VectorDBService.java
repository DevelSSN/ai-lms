package com.ailms.orchestrator.service;

import com.ailms.common.entity.ContentDocument;
import com.ailms.common.entity.ContentEmbedding;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkiverse.langchain4j.redis.RedisEmbeddingStore;
import io.quarkus.hibernate.orm.panache.Panache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class VectorDBService {

  private final EmbeddingStore<TextSegment> embeddingStore;

  @Inject
  public VectorDBService(@Any Instance<EmbeddingStore<TextSegment>> stores) {
    this.embeddingStore =
        stores.stream()
            .filter(s -> !(s instanceof RedisEmbeddingStore))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("No non-Redis EmbeddingStore available"));
  }

  @Inject EmbeddingModel embeddingModel;

  @Transactional
  public void ingestDocumentChunks(List<String> chunks, String documentId, String contentType) {
    if (chunks == null || chunks.isEmpty()) {
      log.info("No chunks to ingest for documentId={}", documentId);
      return;
    }
    long existing = countEmbeddings(documentId);
    if (existing > 0) {
      log.info("Document already indexed documentId={} chunks={}, skipping", documentId, existing);
      return;
    }

    String source = "doc:" + documentId;
    for (String chunk : chunks) {
      Map<String, Object> meta = Map.of("source", source, "type", contentType);
      TextSegment segment = TextSegment.from(chunk, Metadata.from(meta));

      Embedding embedding = embeddingModel.embed(segment).content();
      embeddingStore.add(embedding, segment);

      ContentEmbedding pgv = new ContentEmbedding();
      pgv.documentId = documentId;
      pgv.source = source;
      pgv.contentType = contentType;
      pgv.embedding = embedding.vector();
      pgv.textSegment = chunk;
      Panache.getEntityManager().persist(pgv);
    }

    ContentDocument doc = Panache.getEntityManager().find(ContentDocument.class, documentId);
    if (doc != null) {
      doc.status = "INDEXED";
      doc.processedAt = Instant.now();
      Panache.getEntityManager().merge(doc);
    }

    log.info(
        "Ingested {} chunks for documentId={} type={} (Qdrant + pgvector)",
        chunks.size(),
        documentId,
        contentType);
  }

  public List<String> retrieveRelevantContext(String query, int maxResults) {
    return retrieveRelevantContext(query, maxResults, null);
  }

  public List<String> retrieveRelevantContext(String query, int maxResults, String sourcePrefix) {
    Embedding queryEmbedding = embeddingModel.embed(query).content();

    int fetchSize = sourcePrefix == null ? maxResults : maxResults * 3;
    EmbeddingSearchRequest request =
        EmbeddingSearchRequest.builder()
            .queryEmbedding(queryEmbedding)
            .maxResults(fetchSize)
            .build();

    List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches();

    return matches.stream()
        .filter(
            m -> {
              if (sourcePrefix == null) return true;
              String src = m.embedded().metadata().getString("source");
              return src != null && src.startsWith(sourcePrefix);
            })
        .limit(maxResults)
        .map(match -> match.embedded().text())
        .toList();
  }

  private long countEmbeddings(String documentId) {
    try {
      return Panache.getEntityManager()
          .createQuery(
              "select count(e) from ContentEmbedding e where e.documentId = :did", Long.class)
          .setParameter("did", documentId)
          .getSingleResult();
    } catch (NoResultException e) {
      return 0;
    }
  }
}
