package com.ailms.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "content_embeddings")
public class ContentEmbedding {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  public String id;

  public String documentId;
  public String source;
  public String contentType;

  @JdbcTypeCode(SqlTypes.VECTOR)
  @Array(length = 768)
  @Column(columnDefinition = "vector(768)")
  public float[] embedding;

  @Column(columnDefinition = "TEXT")
  public String textSegment;

  public Instant createdAt;

  @PrePersist
  void onCreate() {
    createdAt = Instant.now();
  }
}
