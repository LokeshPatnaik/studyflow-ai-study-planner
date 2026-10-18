package edu.studyplanner.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity @Table(name = "study_tasks", indexes = {
    @Index(name = "idx_task_student_date", columnList = "student_id,scheduled_date"),
    @Index(name = "idx_task_plan_status", columnList = "plan_id,status")
})
public class StudyTask {
    @Id public UUID id;
    @Column(name = "plan_id", nullable = false) public UUID planId;
    @Column(name = "student_id", nullable = false) public UUID studentId;
    @Column(name = "subject_id", nullable = false) public UUID subjectId;
    @Column(name = "topic_id") public UUID topicId;
    @Column(nullable = false, length = 180) public String title;
    @Column(length = 1000) public String description;
    @Column(name = "scheduled_date", nullable = false) public LocalDate scheduledDate;
    @Column(name = "start_time") public LocalTime startTime;
    @Column(name = "end_time") public LocalTime endTime;
    @Column(name = "duration_minutes", nullable = false) public int durationMinutes;
    @Column(nullable = false, length = 12) public String priority;
    @Column(nullable = false, length = 16) public String status;
    @Column(name = "session_type", nullable = false, length = 24) public String sessionType = "LEARN";
    @Column(name = "revision_number", nullable = false) public int revisionNumber;
    @Column(name = "actual_minutes") public Integer actualMinutes;
    @Column(length = 1000) public String notes;
    @Column public Integer confidence;
    @Column(name = "exam_date") public LocalDate examDate;
    @Column(name = "completed_at") public Instant completedAt;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    protected StudyTask() {}
    public StudyTask(UUID planId, UUID studentId, UUID subjectId, UUID topicId, String title, String description,
                     LocalDate date, LocalTime start, LocalTime end, int duration, String priority, String status, LocalDate examDate) {
        this.id = UUID.randomUUID(); this.planId = planId; this.studentId = studentId; this.subjectId = subjectId;
        this.topicId = topicId; this.title = title; this.description = description; this.scheduledDate = date;
        this.startTime = start; this.endTime = end; this.durationMinutes = duration; this.priority = priority;
        this.status = status; this.examDate = examDate; this.createdAt = Instant.now();
    }
}

