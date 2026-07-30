package com.ailms.orchestrator.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkiverse.langchain4j.redis.RedisEmbeddingStore;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VectorDBServiceTest {

  @Mock EmbeddingStore<TextSegment> embeddingStore;
  @Mock EmbeddingModel embeddingModel;

  @Test
  void constructWithNonRedisStore() {
    Instance<EmbeddingStore<TextSegment>> stores = mock(Instance.class);
    when(stores.stream()).thenReturn(Stream.of(embeddingStore));

    VectorDBService svc = new VectorDBService(stores);
    assertNotNull(svc);
  }

  @Test
  void constructSkipsRedisStore() {
    EmbeddingStore<TextSegment> redisStore = mock(RedisEmbeddingStore.class);
    Instance<EmbeddingStore<TextSegment>> stores = mock(Instance.class);
    when(stores.stream()).thenReturn(Stream.of(redisStore, embeddingStore));

    VectorDBService svc = new VectorDBService(stores);
    assertNotNull(svc);
  }

  @Test
  @Disabled("Needs Quarkus Arc for Panache")
  void ingestDocument_callsStore() {
    when(embeddingModel.embed(any(TextSegment.class)))
        .thenReturn(Response.from(new Embedding(new float[]{0.1f, 0.2f, 0.3f})));

    Instance<EmbeddingStore<TextSegment>> stores = mock(Instance.class);
    when(stores.stream()).thenReturn(Stream.of(embeddingStore));

    VectorDBService svc = new VectorDBService(stores);
    svc.embeddingModel = embeddingModel;
    svc.ingestDocument("test content", "source-1", "type-1");

    verify(embeddingStore).add(any(Embedding.class), any(TextSegment.class));
  }

  @Test
  void retrieveRelevantContext() {
    when(embeddingModel.embed(any(String.class)))
        .thenReturn(Response.from(new Embedding(new float[]{0.1f, 0.2f, 0.3f})));

    EmbeddingMatch<TextSegment> match = new EmbeddingMatch<>(
        0.95, "id-1", new Embedding(new float[]{0.1f, 0.2f, 0.3f}), TextSegment.from("matched text"));
    when(embeddingStore.search(any(EmbeddingSearchRequest.class)))
        .thenReturn(new EmbeddingSearchResult<>(List.of(match)));

    Instance<EmbeddingStore<TextSegment>> stores = mock(Instance.class);
    when(stores.stream()).thenReturn(Stream.of(embeddingStore));

    VectorDBService svc = new VectorDBService(stores);
    svc.embeddingModel = embeddingModel;

    List<String> results = svc.retrieveRelevantContext("test query", 3);
    assertEquals(1, results.size());
    assertEquals("matched text", results.get(0));
  }

  @Test
  void retrieveRelevantContext_emptyResults() {
    when(embeddingModel.embed(any(String.class)))
        .thenReturn(Response.from(new Embedding(new float[]{0.1f, 0.2f, 0.3f})));
    when(embeddingStore.search(any(EmbeddingSearchRequest.class)))
        .thenReturn(new EmbeddingSearchResult<>(List.of()));

    Instance<EmbeddingStore<TextSegment>> stores = mock(Instance.class);
    when(stores.stream()).thenReturn(Stream.of(embeddingStore));

    VectorDBService svc = new VectorDBService(stores);
    svc.embeddingModel = embeddingModel;

    assertTrue(svc.retrieveRelevantContext("test", 3).isEmpty());
  }
}
