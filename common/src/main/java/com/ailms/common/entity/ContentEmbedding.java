package com.ailms.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "content_embeddings")
public class ContentEmbedding {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  public String id;

  public String documentId;
  public String source;
  public String contentType;

  @Column(columnDefinition = "vector(384)")
  public float[] embedding;

  @Column(columnDefinition = "TEXT")
  public String textSegment;

  public Instant createdAt;

  @PrePersist
  void onCreate() {
    createdAt = Instant.now();
  }
}
