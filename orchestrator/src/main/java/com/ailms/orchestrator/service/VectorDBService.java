package com.ailms.orchestrator.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@ApplicationScoped
public class VectorDBService {

  @Inject EmbeddingStore<TextSegment> embeddingStore;

  @Inject EmbeddingModel embeddingModel;

  public void ingestDocument(String content, String source, String contentType) {
    TextSegment segment =
        TextSegment.from(content, Metadata.from("source", source, "type", contentType));

    Embedding embedding = embeddingModel.embed(segment).content();
    embeddingStore.add(embedding, segment);

    log.info("Ingested document from source={} type={}", source, contentType);
  }

  public List<String> retrieveRelevantContext(String query, int maxResults) {
    Embedding queryEmbedding = embeddingModel.embed(query).content();

    EmbeddingSearchRequest request =
        EmbeddingSearchRequest.builder()
            .queryEmbedding(queryEmbedding)
            .maxResults(maxResults)
            .build();

    List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches();

    return matches.stream()
        .map(match -> match.embedded().text())
        .toList();
  }
}
