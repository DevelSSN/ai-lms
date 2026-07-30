package com.ailms.orchestrator.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.IOException;

@Slf4j
@ApplicationScoped
public class ObjectStorageService {

  @Inject S3Client s3;

  static final String BUCKET = "ailms-content";

  public byte[] readFile(String storagePath) {
    try {
      GetObjectRequest req = GetObjectRequest.builder()
          .bucket(BUCKET)
          .key(storagePath)
          .build();
      try (ResponseInputStream<?> is = s3.getObject(req)) {
        return is.readAllBytes();
      }
    } catch (NoSuchKeyException e) {
      log.warn("File not found in S3: {}", storagePath);
      return null;
    } catch (IOException e) {
      log.error("Failed to read file from S3: {}", storagePath, e);
      return null;
    }
  }
}
