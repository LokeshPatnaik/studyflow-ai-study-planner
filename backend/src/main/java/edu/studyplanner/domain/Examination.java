package edu.studyplanner.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity @Table(name = "examinations", indexes = @Index(name = "idx_exam_student_date", columnList = "student_id,exam_date"))
public class Examination {
    @Id public UUID id;
    @Column(name = "student_id", nullable = false) public UUID studentId;
    @Column(name = "subject_id", nullable = false) public UUID subjectId;
    @Column(nullable = false, length = 120) public String name;
    @Column(name = "exam_date", nullable = false) public LocalDate examDate;
    protected Examination() {}
    public Examination(UUID studentId, UUID subjectId, String name, LocalDate examDate) {
        this.id = UUID.randomUUID(); this.studentId = studentId; this.subjectId = subjectId; this.name = name; this.examDate = examDate;
    }
}

