package edu.studyplanner.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "schedule_revisions", indexes = @Index(name = "idx_revision_student_created", columnList = "student_id,created_at"))
public class ScheduleRevision {
    @Id public UUID id;
    @Column(name = "student_id", nullable = false) public UUID studentId;
    @Column(name = "plan_id", nullable = false) public UUID planId;
    @Column(nullable = false, length = 20) public String status;
    @Column(nullable = false, length = 1400) public String reason;
    @Column(name = "proposed_json", nullable = false, columnDefinition = "text") public String proposedJson;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    @Column(name = "decided_at") public Instant decidedAt;
    protected ScheduleRevision() {}
    public ScheduleRevision(UUID studentId, UUID planId, String reason, String proposedJson) {
        this.id = UUID.randomUUID(); this.studentId = studentId; this.planId = planId; this.status = "PENDING";
        this.reason = reason; this.proposedJson = proposedJson; this.createdAt = Instant.now();
    }
}

