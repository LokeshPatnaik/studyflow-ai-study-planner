package edu.studyplanner.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "subjects", indexes = @Index(name = "idx_subject_student", columnList = "student_id"))
public class Subject {
    @Id public UUID id;
    @Column(name = "student_id", nullable = false) public UUID studentId;
    @Column(nullable = false, length = 120) public String name;
    @Column(nullable = false, length = 16) public String color = "#8b7cff";
    @Column(nullable = false) public int priority = 3;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    protected Subject() {}
    public Subject(UUID studentId, String name, String color, int priority) {
        this.id = UUID.randomUUID(); this.studentId = studentId; this.name = name;
        this.color = color; this.priority = priority; this.createdAt = Instant.now();
    }
}

