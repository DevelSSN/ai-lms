package com.ailms.orchestrator.service;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DocumentParserServiceTest {

  DocumentParserService service = new DocumentParserService();

  @Test
  void parsePlainText() {
    byte[] content =
        "Hello, this is plain text.\nWith multiple lines.".getBytes(StandardCharsets.UTF_8);
    DocumentParserService.ParseResult result = service.parse(content, "test.txt", "text/plain");
    assertTrue(result.isSuccess());
    assertNotNull(result.text());
    assertTrue(result.text().contains("plain text"));
  }

  @Test
  void isSupported_textPlain() {
    assertTrue(service.isSupported("text/plain"));
  }

  @Test
  void isSupported_pdf() {
    assertTrue(service.isSupported("application/pdf"));
  }

  @Test
  void isSupported_docx() {
    assertTrue(
        service.isSupported(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
  }

  @Test
  void isSupported_image() {
    assertTrue(service.isSupported("image/png"));
    assertTrue(service.isSupported("image/jpeg"));
  }

  @Test
  void isNotSupported_video() {
    assertFalse(service.isSupported("video/mp4"));
    assertFalse(service.isSupported("audio/mpeg"));
  }

  @Test
  void parseUnsupportedType() {
    byte[] content = "test".getBytes();
    DocumentParserService.ParseResult result = service.parse(content, "test.mp4", "video/mp4");
    assertFalse(result.isSuccess());
    assertNotNull(result.error());
    assertTrue(result.error().contains("Unsupported file type"));
  }

  @Test
  void parseEmptyContent() {
    byte[] content = new byte[0];
    DocumentParserService.ParseResult result = service.parse(content, "empty.txt", "text/plain");
    assertFalse(result.isSuccess());
  }

  @Test
  void parseNullContentType() {
    assertFalse(service.isSupported(null));
  }
}
