package com.ailms.orchestrator.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.ailms.common.entity.ContentEmbedding;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import io.quarkiverse.langchain4j.redis.RedisEmbeddingStore;
import jakarta.enterprise.inject.Instance;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VectorDBServiceTest {

  @Mock EmbeddingStore<TextSegment> embeddingStore;
  @Mock EmbeddingModel embeddingModel;
  @Mock ContentEmbeddingRepository contentEmbeddingRepository;

  private VectorDBService newService() {
    Instance<EmbeddingStore<TextSegment>> stores = mock(Instance.class);
    when(stores.stream()).thenReturn(Stream.of(embeddingStore));
    VectorDBService svc = new VectorDBService(stores);
    svc.embeddingModel = embeddingModel;
    svc.contentEmbeddingRepository = contentEmbeddingRepository;
    return svc;
  }

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
  void ingestDocumentChunks_purgesThenEmbedsThenPersists() {
    when(embeddingModel.embed(any(TextSegment.class)))
        .thenReturn(Response.from(new Embedding(new float[] {0.1f, 0.2f, 0.3f})));

    VectorDBService svc = newService();
    svc.ingestDocumentChunks(List.of("chunk one", "chunk two"), "doc-1", "document");

    InOrder order = inOrder(embeddingStore);
    order.verify(embeddingStore).removeAll(any(Filter.class));
    order.verify(embeddingStore, times(2)).add(any(Embedding.class), any(TextSegment.class));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<ContentEmbedding>> captor = ArgumentCaptor.forClass(List.class);
    verify(contentEmbeddingRepository)
        .replaceAll(eq("doc-1"), eq("doc:doc-1"), eq("document"), captor.capture());
    List<ContentEmbedding> rows = captor.getValue();
    assertEquals(2, rows.size());
    assertEquals("chunk one", rows.get(0).textSegment);
    assertEquals("doc:doc-1", rows.get(0).source);
    assertEquals("document", rows.get(0).contentType);
    assertEquals(3, rows.get(0).embedding.length);
  }

  @Test
  void ingestDocumentChunks_embedFailurePropagatesAndSkipsPersistence() {
    when(embeddingModel.embed(any(TextSegment.class)))
        .thenReturn(Response.from(new Embedding(new float[] {0.1f, 0.2f, 0.3f})))
        .thenThrow(new RuntimeException("embed failed"));

    VectorDBService svc = newService();
    assertThrows(
        RuntimeException.class,
        () -> svc.ingestDocumentChunks(List.of("a", "b"), "doc-1", "document"));
    verify(contentEmbeddingRepository, never())
        .replaceAll(any(), any(), any(), any());
  }

  @Test
  void retrieveRelevantContext() {
    when(embeddingModel.embed(any(String.class)))
        .thenReturn(Response.from(new Embedding(new float[] {0.1f, 0.2f, 0.3f})));

    EmbeddingMatch<TextSegment> match =
        new EmbeddingMatch<>(
            0.95,
            "id-1",
            new Embedding(new float[] {0.1f, 0.2f, 0.3f}),
            TextSegment.from("matched text"));
    when(embeddingStore.search(any(EmbeddingSearchRequest.class)))
        .thenReturn(new EmbeddingSearchResult<>(List.of(match)));

    VectorDBService svc = newService();

    List<String> results = svc.retrieveRelevantContext("test query", 3);
    assertEquals(1, results.size());
    assertEquals("matched text", results.get(0));
  }

  @Test
  void retrieveRelevantContext_filtersBySourcePrefix() {
    when(embeddingModel.embed(any(String.class)))
        .thenReturn(Response.from(new Embedding(new float[] {0.1f, 0.2f, 0.3f})));

    EmbeddingMatch<TextSegment> match =
        new EmbeddingMatch<>(
            0.95,
            "id-1",
            new Embedding(new float[] {0.1f, 0.2f, 0.3f}),
            TextSegment.from("doc text", Metadata.from(java.util.Map.of("source", "doc:abc"))));
    when(embeddingStore.search(any(EmbeddingSearchRequest.class)))
        .thenReturn(new EmbeddingSearchResult<>(List.of(match)));

    VectorDBService svc = newService();

    List<String> results = svc.retrieveRelevantContext("test query", 3, "doc:");
    assertEquals(List.of("doc text"), results);
  }

  @Test
  void retrieveRelevantContext_filtersByPredicate() {
    when(embeddingModel.embed(any(String.class)))
        .thenReturn(Response.from(new Embedding(new float[] {0.1f, 0.2f, 0.3f})));

    EmbeddingMatch<TextSegment> match =
        new EmbeddingMatch<>(
            0.95,
            "id-1",
            new Embedding(new float[] {0.1f, 0.2f, 0.3f}),
            TextSegment.from("doc text", Metadata.from(java.util.Map.of("source", "doc:abc"))));
    when(embeddingStore.search(any(EmbeddingSearchRequest.class)))
        .thenReturn(new EmbeddingSearchResult<>(List.of(match)));

    VectorDBService svc = newService();

    List<String> results =
        svc.retrieveRelevantContext("test query", 3, s -> s != null && s.equals("doc:abc"));
    assertEquals(List.of("doc text"), results);
  }

  @Test
  void retrieveRelevantContext_emptyResults() {
    when(embeddingModel.embed(any(String.class)))
        .thenReturn(Response.from(new Embedding(new float[] {0.1f, 0.2f, 0.3f})));
    when(embeddingStore.search(any(EmbeddingSearchRequest.class)))
        .thenReturn(new EmbeddingSearchResult<>(List.of()));

    VectorDBService svc = newService();

    assertTrue(svc.retrieveRelevantContext("test", 3).isEmpty());
  }
}
