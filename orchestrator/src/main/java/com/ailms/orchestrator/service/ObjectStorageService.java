package com.ailms.orchestrator.service;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

@Slf4j
@ApplicationScoped
@Startup
public class ObjectStorageService {

  static final String BUCKET = "ailms-content";

  private static final int MAX_ATTEMPTS = 5;

  private static final long RETRY_DELAY_MS = 1000;

  @Inject S3Client s3;

  @PostConstruct
  void ensureBucket() {
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        s3.headBucket(HeadBucketRequest.builder().bucket(BUCKET).build());
        log.info("S3 bucket {} already exists", BUCKET);
        return;
      } catch (NoSuchBucketException e) {
        try {
          s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
          log.info("Created S3 bucket {}", BUCKET);
          return;
        } catch (BucketAlreadyOwnedByYouException ignored) {
          log.info("S3 bucket {} already exists", BUCKET);
          return;
        } catch (Exception createErr) {
          log.warn(
              "Failed to create S3 bucket {} (attempt {}/{}): {}",
              BUCKET,
              attempt,
              MAX_ATTEMPTS,
              createErr.getMessage());
        }
      } catch (Exception e) {
        log.warn(
            "S3 headBucket failed for {} (attempt {}/{}): {}",
            BUCKET,
            attempt,
            MAX_ATTEMPTS,
            e.getMessage());
      }
      if (attempt < MAX_ATTEMPTS) {
        try {
          Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          return;
        }
      }
    }
  }

  /**
   * Reads a stored file. Returns null only when the key does not exist; infrastructure and I/O
   * failures are surfaced as {@link ObjectStorageException} so callers fail closed instead of
   * silently treating an outage as a missing file.
   */
  public byte[] readFile(String storagePath) {
    try {
      GetObjectRequest req = GetObjectRequest.builder().bucket(BUCKET).key(storagePath).build();
      try (ResponseInputStream<?> is = s3.getObject(req)) {
        return is.readAllBytes();
      }
    } catch (NoSuchKeyException e) {
      log.warn("File not found in S3: {}", storagePath);
      return null;
    } catch (IOException | RuntimeException e) {
      log.error("Failed to read file from S3: {}", storagePath, e);
      throw new ObjectStorageException("Failed to read file from S3: " + storagePath, e);
    }
  }
}
