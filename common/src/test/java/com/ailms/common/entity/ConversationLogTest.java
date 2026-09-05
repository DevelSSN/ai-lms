package com.ailms.common.entity;

import static org.junit.jupiter.api.Assertions.*;

import com.ailms.common.enums.ContentStatus;
import org.junit.jupiter.api.Test;

class ConversationLogTest {

  @Test
  void createUserMessage() {
    ConversationLog log = new ConversationLog();
    log.userId = "user-1";
    log.sessionId = "sess-1";
    log.role = "user";
    log.message = "hello";
    assertEquals("user-1", log.userId);
    assertEquals("sess-1", log.sessionId);
    assertEquals("user", log.role);
    assertEquals("hello", log.message);
  }

  @Test
  void createAssistantMessage() {
    ConversationLog log = new ConversationLog();
    log.userId = "user-1";
    log.sessionId = "sess-1";
    log.role = "assistant";
    log.message = "Hi there!";
    log.assistantMessage = "Hi there!";
    log.agentType = "CONVERSATION";
    assertEquals("assistant", log.role);
    assertEquals("Hi there!", log.assistantMessage);
    assertEquals("CONVERSATION", log.agentType);
  }

  @Test
  void prePersistSetsTimestamp() {
    ConversationLog log = new ConversationLog();
    assertNull(log.timestamp);
    log.onCreate();
    assertNotNull(log.timestamp);
  }
}

class ContentDocumentTest {

  @Test
  void create() {
    ContentDocument doc = new ContentDocument();
    doc.userId = "user-1";
    doc.fileName = "test.pdf";
    doc.fileType = "application/pdf";
    doc.fileSize = 1024L;
    doc.storagePath = "uploads/user-1/uuid_test.pdf";
    assertEquals("user-1", doc.userId);
    assertEquals("test.pdf", doc.fileName);
  }

  @Test
  void prePersistSetsDefaults() {
    ContentDocument doc = new ContentDocument();
    assertNull(doc.uploadedAt);
    assertNull(doc.status);
    doc.onCreate();
    assertNotNull(doc.uploadedAt);
    assertEquals(ContentStatus.UPLOADED, doc.status);
  }
}

class UserProfileTest {

  @Test
  void create() {
    UserProfile p = new UserProfile();
    p.externalId = "user-1";
    p.name = "Test User";
    p.email = "test@example.com";
    p.knowledgeLevel = "intermediate";
    assertEquals("user-1", p.externalId);
    assertEquals("intermediate", p.knowledgeLevel);
  }

  @Test
  void prePersistSetsCreatedAt() {
    UserProfile p = new UserProfile();
    assertNull(p.createdAt);
    p.onCreate();
    assertNotNull(p.createdAt);
    assertNotNull(p.updatedAt);
  }
}

class ContentEmbeddingTest {

  @Test
  void create() {
    ContentEmbedding e = new ContentEmbedding();
    e.documentId = "doc-1";
    e.source = "user-1";
    e.contentType = "conversation";
    e.embedding = new float[] {0.1f, 0.2f, 0.3f};
    e.textSegment = "sample text";
    assertEquals("doc-1", e.documentId);
    assertEquals(3, e.embedding.length);
  }

  @Test
  void prePersistSetsCreatedAt() {
    ContentEmbedding e = new ContentEmbedding();
    assertNull(e.createdAt);
    e.onCreate();
    assertNotNull(e.createdAt);
  }
}
