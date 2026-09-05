package com.ailms.orchestrator.service;

import com.ailms.common.constants.VectorSourceKeys;
import com.ailms.common.entity.ContentDocument;
import com.ailms.common.entity.ContentEmbedding;
import com.ailms.common.enums.ContentStatus;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import io.quarkiverse.langchain4j.redis.RedisEmbeddingStore;
import io.quarkus.hibernate.orm.panache.Panache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

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

  @ConfigProperty(name = "ailms.rag.min-score", defaultValue = "0.5")
  double minScore;

  @Transactional
  public void ingestDocumentChunks(List<String> chunks, String documentId, String contentType) {
    if (chunks == null || chunks.isEmpty()) {
      log.info("No chunks to ingest for documentId={}", documentId);
      return;
    }

    String source = VectorSourceKeys.document(documentId);

    // Purge any prior vectors for this document so re-ingest is a clean, idempotent replace rather
    // than skipping on partial writes or duplicating across the two stores (Qdrant + pgvector).
    try {
      embeddingStore.removeAll(MetadataFilterBuilder.metadataKey("source").isEqualTo(source));
    } catch (Exception e) {
      log.warn(
          "Failed to purge previous Qdrant vectors for documentId={}: {}", documentId, e.getMessage());
    }
    Panache.getEntityManager()
        .createQuery("delete from ContentEmbedding e where e.documentId = :did")
        .setParameter("did", documentId)
        .executeUpdate();

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
      doc.status = ContentStatus.INDEXED;
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
    return retrieveRelevantContext(query, maxResults, (Predicate<String>) null);
  }

  public List<String> retrieveRelevantContext(String query, int maxResults, String sourcePrefix) {
    Predicate<String> filter =
        sourcePrefix == null ? null : source -> source != null && source.startsWith(sourcePrefix);
    return retrieveRelevantContext(query, maxResults, filter);
  }

  public List<String> retrieveRelevantContext(
      String query, int maxResults, Predicate<String> sourceFilter) {
    Embedding queryEmbedding = embeddingModel.embed(query).content();

    int fetchSize = sourceFilter == null ? maxResults : maxResults * 3;
    EmbeddingSearchRequest request =
        EmbeddingSearchRequest.builder()
            .queryEmbedding(queryEmbedding)
            .maxResults(fetchSize)
            .build();

    List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches();

    return matches.stream()
        .filter(m -> m.score() >= minScore)
        .filter(
            m ->
                sourceFilter == null
                    || sourceFilter.test(m.embedded().metadata().getString("source")))
        .limit(maxResults)
        .map(match -> match.embedded().text())
        .toList();
  }
}
