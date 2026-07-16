package com.ailms.common.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "assessment_items")
public class AssessmentItem {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  public String id;

  public String userId;
  public String contentId;
  public String questionType;
  public String difficulty;

  @Column(columnDefinition = "TEXT")
  public String question;

  @Column(columnDefinition = "TEXT")
  public String options;

  @Column(columnDefinition = "TEXT")
  public String correctAnswer;

  @Column(columnDefinition = "TEXT")
  public String topicTags;

  public Instant createdAt;
  public Instant answeredAt;
  public Boolean answeredCorrectly;

  @PrePersist
  void onCreate() {
    createdAt = Instant.now();
  }
}
