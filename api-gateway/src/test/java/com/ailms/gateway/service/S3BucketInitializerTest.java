package com.ailms.gateway.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

@ExtendWith(MockitoExtension.class)
class S3BucketInitializerTest {

  @Mock S3Client s3;

  @Test
  void createsBucketWhenMissing() {
    when(s3.headBucket(any(HeadBucketRequest.class)))
        .thenThrow(NoSuchBucketException.builder().build());
    when(s3.createBucket(any(CreateBucketRequest.class)))
        .thenReturn(CreateBucketResponse.builder().build());

    S3BucketInitializer initializer = new S3BucketInitializer();
    initializer.s3 = s3;
    initializer.ensureBucket();

    verify(s3).createBucket(any(CreateBucketRequest.class));
  }

  @Test
  void keepsExistingBucket() {
    when(s3.headBucket(any(HeadBucketRequest.class)))
        .thenReturn(HeadBucketResponse.builder().build());

    S3BucketInitializer initializer = new S3BucketInitializer();
    initializer.s3 = s3;
    initializer.ensureBucket();

    verify(s3, never()).createBucket(any(CreateBucketRequest.class));
  }

  @Test
  void toleratesTransientHeadBucketFailure() {
    when(s3.headBucket(any(HeadBucketRequest.class)))
        .thenThrow(new RuntimeException("Connection error"))
        .thenReturn(HeadBucketResponse.builder().build());

    S3BucketInitializer initializer = new S3BucketInitializer();
    initializer.s3 = s3;
    initializer.ensureBucket();

    verify(s3, never()).createBucket(any(CreateBucketRequest.class));
  }
}
