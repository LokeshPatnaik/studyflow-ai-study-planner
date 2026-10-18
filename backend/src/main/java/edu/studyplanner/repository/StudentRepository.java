package edu.studyplanner.repository;
import edu.studyplanner.domain.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
public interface StudentRepository extends JpaRepository<Student, UUID> {
    Optional<Student> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
}

