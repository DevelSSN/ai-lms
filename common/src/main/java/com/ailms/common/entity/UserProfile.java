package com.ailms.common.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public String id;

    @Column(unique = true, nullable = false)
    public String externalId;

    public String name;
    public String email;
    public String knowledgeLevel;
    public String preferredLearningStyle;

    @Column(columnDefinition = "TEXT")
    public String interests;

    @Column(columnDefinition = "TEXT")
    public String behavioralTraits;

    @Column(columnDefinition = "TEXT")
    public String metadata;

    public Instant createdAt;
    public Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
