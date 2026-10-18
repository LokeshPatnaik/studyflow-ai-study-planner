package edu.studyplanner.security;

import edu.studyplanner.domain.Student;
import edu.studyplanner.repository.StudentRepository;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class StudentUserDetailsService implements UserDetailsService {
    private final StudentRepository students;
    public StudentUserDetailsService(StudentRepository students) { this.students = students; }
    @Override public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Student student = students.findByEmailIgnoreCase(username).orElseThrow(() -> new UsernameNotFoundException("Student not found"));
        return User.withUsername(student.email).password(student.passwordHash).roles("STUDENT").build();
    }
}

