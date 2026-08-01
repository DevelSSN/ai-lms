package com.ailms.orchestrator.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class ContentDocumentServiceTest {

  @Test
  void chunkText_blankContentReturnsEmpty() {
    assertTrue(ContentDocumentService.chunkText("  ", 100, 10).isEmpty());
    assertTrue(ContentDocumentService.chunkText(null, 100, 10).isEmpty());
  }

  @Test
  void chunkText_smallContentSingleChunk() {
    List<String> chunks = ContentDocumentService.chunkText("short text", 100, 10);
    assertEquals(List.of("short text"), chunks);
  }

  @Test
  void chunkText_splitsLongContentWithinBounds() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 100; i++) {
      sb.append("word ").append(i).append(' ');
    }
    String content = sb.toString();

    List<String> chunks = ContentDocumentService.chunkText(content, 200, 20);

    assertTrue(chunks.size() > 1);
    for (String chunk : chunks) {
      assertTrue(chunk.length() <= 201, "chunk exceeds size: " + chunk.length());
    }
  }

  @Test
  void chunkText_reassemblesOriginalContent() {
    String content = "alpha ".repeat(500);
    List<String> chunks = ContentDocumentService.chunkText(content, 300, 30);

    StringBuilder joined = new StringBuilder();
    for (int i = 0; i < chunks.size(); i++) {
      String chunk = chunks.get(i);
      if (i > 0) {
        int overlap = 30;
        joined.delete(joined.length() - overlap, joined.length());
      }
      joined.append(chunk);
    }
    assertEquals(content, joined.toString());
  }

  @Test
  void chunkText_breaksOnNewlineBoundary() {
    String content = ("paragraph line \n".repeat(50));
    List<String> chunks = ContentDocumentService.chunkText(content, 200, 20);

    for (String chunk : chunks) {
      if (chunk.length() > 100) {
        assertTrue(chunk.endsWith("\n") || chunk.endsWith(" "), "chunk should end on boundary");
      }
    }
  }
}
