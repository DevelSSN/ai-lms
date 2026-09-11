package com.ailms.orchestrator.service;

import com.ailms.common.constants.VectorSourceKeys;
import com.ailms.common.dto.RetrievedChunk;
import com.ailms.common.entity.ContentEmbedding;
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
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

  @Inject ContentEmbeddingRepository contentEmbeddingRepository;

  @ConfigProperty(name = "ailms.rag.min-score", defaultValue = "0.5")
  double minScore;

  public void ingestDocumentChunks(List<String> chunks, String documentId, String contentType) {
    if (chunks == null || chunks.isEmpty()) {
      log.info("No chunks to ingest for documentId={}", documentId);
      return;
    }

    String source = VectorSourceKeys.document(documentId);

    List<ContentEmbedding> rows = new ArrayList<>(chunks.size());
    for (int i = 0; i < chunks.size(); i++) {
      String chunk = chunks.get(i);
      Map<String, Object> meta =
          Map.of("source", source, "type", contentType, "chunkIndex", i);
      TextSegment segment = TextSegment.from(chunk, Metadata.from(meta));

      Embedding embedding = embeddingModel.embed(segment).content();
      embeddingStore.add(embedding, segment);

      ContentEmbedding pgv = new ContentEmbedding();
      pgv.documentId = documentId;
      pgv.source = source;
      pgv.contentType = contentType;
      pgv.embedding = embedding.vector();
      pgv.textSegment = chunk;
      rows.add(pgv);
    }

    contentEmbeddingRepository.replaceAll(documentId, source, contentType, rows);

    try {
      embeddingStore.removeAll(MetadataFilterBuilder.metadataKey("source").isEqualTo(source));
    } catch (Exception e) {
      log.warn(
          "Failed to purge stale Qdrant vectors for documentId={}: {}",
          documentId,
          e.getMessage());
    }

    log.info(
        "Ingested {} chunks for documentId={} type={} (Qdrant + pgvector)",
        chunks.size(),
        documentId,
        contentType);
  }

  public List<RetrievedChunk> retrieveRelevantContext(String query, int maxResults) {
    return search(query, maxResults, null);
  }

  public List<RetrievedChunk> retrieveRelevantContext(String query, int maxResults, String sourceKey) {
    Filter filter =
        sourceKey == null
            ? null
            : MetadataFilterBuilder.metadataKey("source").isEqualTo(sourceKey);
    return search(query, maxResults, filter);
  }

  private List<RetrievedChunk> search(String query, int maxResults, Filter filter) {
    Embedding queryEmbedding = embeddingModel.embed(query).content();

    EmbeddingSearchRequest request =
        EmbeddingSearchRequest.builder()
            .queryEmbedding(queryEmbedding)
            .maxResults(maxResults)
            .filter(filter)
            .build();

    List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches();

    return matches.stream()
        .filter(m -> m.score() >= minScore)
        .limit(maxResults)
        .map(
            match -> {
              Map<String, Object> meta =
                  match.embedded().metadata() == null
                      ? Map.of()
                      : match.embedded().metadata().toMap();
              Object source = meta.get("source");
              Object idx = meta.get("chunkIndex");
              int chunkIndex =
                  idx instanceof Number n ? n.intValue() : parseIntQuietly(idx);
              return new RetrievedChunk(
                  match.embedded().text(),
                  source instanceof String s ? s : null,
                  match.score(),
                  chunkIndex);
            })
        .toList();
  }

  private static int parseIntQuietly(Object value) {
    if (value == null) return -1;
    try {
      return Integer.parseInt(value.toString());
    } catch (NumberFormatException e) {
      return -1;
    }
  }
}
