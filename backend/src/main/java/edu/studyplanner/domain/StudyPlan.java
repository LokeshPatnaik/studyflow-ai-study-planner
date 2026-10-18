package edu.studyplanner.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "study_plans", indexes = @Index(name = "idx_plan_student_status", columnList = "student_id,status"))
public class StudyPlan {
    @Id public UUID id;
    @Column(name = "student_id", nullable = false) public UUID studentId;
    @Column(nullable = false, length = 120) public String title;
    @Column(nullable = false, length = 24) public String status;
    @Column(name = "ai_mode", nullable = false, length = 24) public String aiMode;
    @Column(length = 1400) public String summary;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    protected StudyPlan() {}
    public StudyPlan(UUID studentId, String title, String status, String aiMode, String summary) {
        this.id = UUID.randomUUID(); this.studentId = studentId; this.title = title; this.status = status;
        this.aiMode = aiMode; this.summary = summary; this.createdAt = Instant.now();
    }
}

