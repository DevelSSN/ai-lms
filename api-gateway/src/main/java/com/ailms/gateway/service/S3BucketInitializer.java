package com.ailms.gateway.service;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

@Slf4j
@ApplicationScoped
@Startup
public class S3BucketInitializer {

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
}
