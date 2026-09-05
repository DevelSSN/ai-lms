package com.ailms.orchestrator.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

@ExtendWith(MockitoExtension.class)
class ObjectStorageServiceTest {

  @Mock S3Client s3;

  @Test
  void ensureBucket_createsWhenMissing() {
    when(s3.headBucket(any(HeadBucketRequest.class)))
        .thenThrow(NoSuchBucketException.builder().build());
    when(s3.createBucket(any(CreateBucketRequest.class)))
        .thenReturn(CreateBucketResponse.builder().build());

    ObjectStorageService service = new ObjectStorageService();
    service.s3 = s3;
    service.ensureBucket();

    verify(s3).createBucket(any(CreateBucketRequest.class));
  }

  @Test
  void ensureBucket_keepsExisting() {
    when(s3.headBucket(any(HeadBucketRequest.class)))
        .thenReturn(HeadBucketResponse.builder().build());

    ObjectStorageService service = new ObjectStorageService();
    service.s3 = s3;
    service.ensureBucket();

    verify(s3, never()).createBucket(any(CreateBucketRequest.class));
  }

  @Test
  void readFile_returnsBytes() throws Exception {
    byte[] expected = "file content".getBytes();
    GetObjectResponse resp = GetObjectResponse.builder().build();
    ResponseInputStream<GetObjectResponse> is =
        new ResponseInputStream<>(resp, new ByteArrayInputStream(expected));

    when(s3.getObject(any(GetObjectRequest.class))).thenReturn(is);

    ObjectStorageService service = new ObjectStorageService();
    service.s3 = s3;

    byte[] result = service.readFile("uploads/test.txt");
    assertArrayEquals(expected, result);
  }

  @Test
  void readFile_notFound_returnsNull() {
    when(s3.getObject(any(GetObjectRequest.class))).thenThrow(NoSuchKeyException.class);

    ObjectStorageService service = new ObjectStorageService();
    service.s3 = s3;

    assertNull(service.readFile("nonexistent"));
  }

  @Test
  void readFile_infraError_throws() {
    when(s3.getObject(any(GetObjectRequest.class)))
        .thenThrow(new RuntimeException("Connection error"));

    ObjectStorageService service = new ObjectStorageService();
    service.s3 = s3;

    assertThrows(ObjectStorageException.class, () -> service.readFile("broken"));
  }
}
