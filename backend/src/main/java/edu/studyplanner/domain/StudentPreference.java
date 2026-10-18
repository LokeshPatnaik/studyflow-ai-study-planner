package edu.studyplanner.domain;

import jakarta.persistence.*;
import java.time.LocalTime;
import java.util.UUID;

@Entity @Table(name = "student_preferences")
public class StudentPreference {
    @Id @Column(name = "student_id") public UUID studentId;
    @Column(name = "daily_study_minutes", nullable = false) public int dailyStudyMinutes = 120;
    @Column(name = "session_minutes", nullable = false) public int sessionMinutes = 50;
    @Column(name = "break_minutes", nullable = false) public int breakMinutes = 10;
    @Column(name = "preferred_days", nullable = false, length = 32) public String preferredDays = "MON,TUE,WED,THU,FRI,SAT,SUN";
    @Column(name = "day_start", nullable = false) public LocalTime dayStart = LocalTime.of(17, 0);
    @Column(name = "day_end", nullable = false) public LocalTime dayEnd = LocalTime.of(21, 0);
    @Column(name = "revision_preference", nullable = false, length = 32) public String revisionPreference = "SPACED";
    protected StudentPreference() {}
    public StudentPreference(UUID studentId) { this.studentId = studentId; }
}

