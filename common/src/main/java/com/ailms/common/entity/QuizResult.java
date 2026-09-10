package com.ailms.common.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** A completed quiz attempt, persisted for result history and profiling feedback. */
@Entity
@Table(name = "quiz_results")
public class QuizResult {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  public String id;

  public String userId;
  public String sessionId;
  public String contentId;

  @Column(columnDefinition = "TEXT")
  public String questions;

  @Column(columnDefinition = "TEXT")
  public String answers;

  public int score;
  public int total;

  public Instant createdAt;

  @PrePersist
  void onCreate() {
    createdAt = Instant.now();
  }
}