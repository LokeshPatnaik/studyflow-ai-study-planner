package edu.studyplanner.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "syllabi", indexes = @Index(name = "idx_syllabus_student_created", columnList = "student_id,created_at"))
public class Syllabus {
    @Id public UUID id;
    @Column(name = "student_id", nullable = false) public UUID studentId;
    @Column(name = "subject_id", nullable = false) public UUID subjectId;
    @Column(name = "source_name", nullable = false, length = 180) public String sourceName;
    @Column(name = "source_type", nullable = false, length = 16) public String sourceType;
    @Column(name = "topic_count", nullable = false) public int topicCount;
    @Column(nullable = false, length = 16) public String status;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    protected Syllabus() {}
    public Syllabus(UUID studentId, UUID subjectId, String sourceName, String sourceType, int topicCount) {
        this.id = UUID.randomUUID(); this.studentId = studentId; this.subjectId = subjectId; this.sourceName = sourceName;
        this.sourceType = sourceType; this.topicCount = topicCount; this.status = "APPROVED"; this.createdAt = Instant.now();
    }
}

