package com.ailms.common.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ContentTypeSupportTest {

  @Test
  void isTextual_recognizesTextFamily() {
    assertTrue(ContentTypeSupport.isTextual("text/plain"));
    assertTrue(ContentTypeSupport.isTextual("text/markdown"));
    assertTrue(ContentTypeSupport.isTextual("text/csv"));
    assertTrue(ContentTypeSupport.isTextual("text/x-script"));
    assertTrue(ContentTypeSupport.isTextual("application/json"));
    assertTrue(ContentTypeSupport.isTextual("application/ld+json"));
  }

  @Test
  void isTextual_rejectsCaseAndWhitespaceVariants() {
    assertTrue(ContentTypeSupport.isTextual("  Text/Plain "));
    assertFalse(ContentTypeSupport.isTextual(null));
    assertFalse(ContentTypeSupport.isTextual(""));
  }

  @Test
  void isParsable_recognizesParserHandledTypes() {
    assertTrue(ContentTypeSupport.isParsable("application/pdf"));
    assertTrue(ContentTypeSupport.isParsable("application/msword"));
    assertTrue(
        ContentTypeSupport.isParsable(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
    assertTrue(ContentTypeSupport.isParsable("image/png"));
    assertFalse(ContentTypeSupport.isParsable("text/plain"));
  }

  @Test
  void isSupported_isUnionOfTextualAndParsable() {
    assertTrue(ContentTypeSupport.isSupported("text/plain"));
    assertTrue(ContentTypeSupport.isSupported("application/pdf"));
    assertTrue(ContentTypeSupport.isSupported("image/jpeg"));
    assertFalse(ContentTypeSupport.isSupported("video/mp4"));
    assertFalse(ContentTypeSupport.isSupported("application/octet-stream"));
    assertFalse(ContentTypeSupport.isSupported(null));
  }
}