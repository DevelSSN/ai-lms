package com.ailms.common.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "content_documents")
public class ContentDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public String id;

    public String userId;
    public String fileName;
    public String fileType;
    public Long fileSize;

    @Column(columnDefinition = "TEXT")
    public String storagePath;

    @Column(columnDefinition = "TEXT")
    public String extractedText;

    @Column(columnDefinition = "TEXT")
    public String topicMappings;

    public String status;
    public Instant uploadedAt;
    public Instant processedAt;

    @PrePersist
    void onCreate() {
        uploadedAt = Instant.now();
        status = "UPLOADED";
    }
}
