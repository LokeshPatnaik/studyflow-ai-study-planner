package edu.studyplanner.security;

import edu.studyplanner.domain.Student;
import edu.studyplanner.repository.StudentRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Component
public class CurrentStudent {
    private final StudentRepository students;
    public CurrentStudent(StudentRepository students) { this.students = students; }
    public Student get() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || "anonymousUser".equals(a.getPrincipal())) throw new ResponseStatusException(UNAUTHORIZED, "Sign in to continue");
        return students.findByEmailIgnoreCase(a.getName()).orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Student account is unavailable"));
    }
}

