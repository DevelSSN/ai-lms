package com.ailms.orchestrator.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToTextContentHandler;

@Slf4j
@ApplicationScoped
public class DocumentParserService {

  private static final Set<String> SUPPORTED_TYPES =
      Set.of(
          "application/pdf",
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
          "application/msword",
          "text/plain",
          "text/html",
          "application/rtf",
          "application/vnd.oasis.opendocument.text",
          "image/png",
          "image/jpeg",
          "image/tiff");

  private final Tika tika = new Tika();

  public ParseResult parse(byte[] fileBytes, String fileName, String contentType) {
    if (!isSupported(contentType)) {
      return new ParseResult(null, "Unsupported file type: " + contentType);
    }

    try (InputStream is = new ByteArrayInputStream(fileBytes)) {
      Metadata metadata = new Metadata();
      metadata.set("resourceName", fileName);

      AutoDetectParser parser = new AutoDetectParser();
      ToTextContentHandler handler = new ToTextContentHandler();
      ParseContext context = new ParseContext();

      parser.parse(is, handler, metadata, context);
      String text = handler.toString();

      if (text.isBlank()) {
        return new ParseResult(null, "No extractable text found in " + fileName);
      }

      log.info(
          "Parsed document fileName={} type={} textLength={}",
          fileName,
          contentType,
          text.length());
      return new ParseResult(text, null);
    } catch (TikaException | IOException | org.xml.sax.SAXException e) {
      log.error("Failed to parse document fileName={}: {}", fileName, e.getMessage());
      return new ParseResult(null, "Failed to parse document: " + e.getMessage());
    }
  }

  public boolean isSupported(String contentType) {
    return contentType != null && SUPPORTED_TYPES.contains(contentType);
  }

  public record ParseResult(String text, String error) {
    public boolean isSuccess() {
      return text != null && error == null;
    }
  }
}
