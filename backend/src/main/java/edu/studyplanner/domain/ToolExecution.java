package edu.studyplanner.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "tool_executions", indexes = @Index(name = "idx_tool_student_created", columnList = "student_id,created_at"))
public class ToolExecution {
    @Id public UUID id;
    @Column(name = "run_id", nullable = false) public UUID runId;
    @Column(name = "student_id", nullable = false) public UUID studentId;
    @Column(name = "tool_name", nullable = false, length = 64) public String toolName;
    @Column(nullable = false, length = 20) public String status;
    @Column(name = "input_summary", length = 1000) public String inputSummary;
    @Column(name = "output_summary", length = 1400) public String outputSummary;
    @Column(name = "error_message", length = 800) public String errorMessage;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    protected ToolExecution() {}
    public ToolExecution(UUID runId, UUID studentId, String toolName, String status, String inputSummary, String outputSummary, String errorMessage) {
        this.id = UUID.randomUUID(); this.runId = runId; this.studentId = studentId; this.toolName = toolName;
        this.status = status; this.inputSummary = inputSummary; this.outputSummary = outputSummary;
        this.errorMessage = errorMessage; this.createdAt = Instant.now();
    }
}

