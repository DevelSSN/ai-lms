package com.ailms.common.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AssessmentItemTest {

  @Test
  void create() {
    AssessmentItem item = new AssessmentItem();
    item.contentId = "content-1";
    item.question = "What is 2+2?";
    item.options = "[\"3\",\"4\",\"5\"]";
    item.correctAnswer = "4";
    item.difficulty = "easy";
    assertEquals("content-1", item.contentId);
    assertEquals("What is 2+2?", item.question);
    assertEquals("4", item.correctAnswer);
  }

  @Test
  void prePersistSetsTimestamps() {
    AssessmentItem item = new AssessmentItem();
    assertNull(item.createdAt);
    item.onCreate();
    assertNotNull(item.createdAt);
  }
}
