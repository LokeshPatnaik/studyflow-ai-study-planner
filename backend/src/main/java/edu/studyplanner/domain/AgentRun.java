package edu.studyplanner.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "agent_runs", indexes = @Index(name = "idx_agent_student_created", columnList = "student_id,created_at"))
public class AgentRun {
    @Id public UUID id;
    @Column(name = "student_id", nullable = false) public UUID studentId;
    @Column(nullable = false, length = 20) public String stage;
    @Column(nullable = false, length = 20) public String status;
    @Column(nullable = false, length = 24) public String mode;
    @Column(length = 1600) public String summary;
    @Column(name = "stage_history", nullable = false, length = 200) public String stageHistory = "OBSERVE";
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    protected AgentRun() {}
    public AgentRun(UUID studentId, String stage, String status, String mode, String summary) {
        this.id = UUID.randomUUID(); this.studentId = studentId; this.stage = stage; this.status = status;
        this.mode = mode; this.summary = summary; this.createdAt = Instant.now();
    }
}

