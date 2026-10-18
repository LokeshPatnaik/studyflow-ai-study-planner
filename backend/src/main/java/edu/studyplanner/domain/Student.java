package edu.studyplanner.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "students", indexes = @Index(name = "idx_students_email", columnList = "email", unique = true))
public class Student {
    @Id public UUID id;
    @Column(nullable = false, length = 100) public String name;
    @Column(nullable = false, length = 254, unique = true) public String email;
    @Column(name = "password_hash", nullable = false) public String passwordHash;
    @Column(name = "is_demo", nullable = false) public boolean demo;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    protected Student() {}
    public Student(String name, String email, String passwordHash, boolean demo) {
        this.id = UUID.randomUUID(); this.name = name; this.email = email;
        this.passwordHash = passwordHash; this.demo = demo; this.createdAt = Instant.now();
    }
}

