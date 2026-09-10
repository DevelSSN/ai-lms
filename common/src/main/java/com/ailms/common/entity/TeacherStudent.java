package com.ailms.common.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "teacher_student", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"teacherId", "studentId"})
})
public class TeacherStudent {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  public String id;

  public String teacherId;

  public String studentId;

  public Instant createdAt = Instant.now();
}