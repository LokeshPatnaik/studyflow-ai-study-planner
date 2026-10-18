package edu.studyplanner.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name = "syllabus_topics", indexes = @Index(name = "idx_topic_subject", columnList = "subject_id"))
public class SyllabusTopic {
    @Id public UUID id;
    @Column(name = "subject_id", nullable = false) public UUID subjectId;
    @Column(nullable = false, length = 180) public String title;
    @Column(length = 120) public String unit;
    @Column(length = 120) public String chapter;
    @Column(length = 1000) public String description;
    @Column(nullable = false, length = 16) public String difficulty = "MEDIUM";
    @Column(name = "estimated_minutes", nullable = false) public int estimatedMinutes = 60;
    @Column(nullable = false) public int importance = 3;
    @Column(nullable = false) public int familiarity = 3;
    @Column(nullable = false) public boolean included = true;
    @Column(name = "original_title", length = 180) public String originalTitle;
    @Column(name = "original_unit", length = 120) public String originalUnit;
    @Column(name = "original_chapter", length = 120) public String originalChapter;
    @Column(name = "original_description", length = 1000) public String originalDescription;
    @Column(name = "source_content", columnDefinition = "TEXT") public String sourceContent;
    @Column(nullable = false) public boolean approved = true;
    protected SyllabusTopic() {}
    public SyllabusTopic(UUID subjectId, String title, String unit, String difficulty, int minutes, boolean approved) {
        this.id = UUID.randomUUID(); this.subjectId = subjectId; this.title = title; this.unit = unit;
        this.difficulty = difficulty; this.estimatedMinutes = minutes; this.approved = approved;
        this.originalTitle = title; this.originalUnit = unit;
    }
}

