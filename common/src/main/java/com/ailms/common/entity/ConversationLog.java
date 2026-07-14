package com.ailms.common.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "conversation_logs")
public class ConversationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public String id;

    public String userId;
    public String sessionId;
    public String role;

    @Column(columnDefinition = "TEXT")
    public String message;

    @Column(columnDefinition = "TEXT")
    public String metadata;

    public Instant timestamp;

    @PrePersist
    void onCreate() {
        timestamp = Instant.now();
    }
}
