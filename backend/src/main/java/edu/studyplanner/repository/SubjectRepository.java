package edu.studyplanner.repository;
import edu.studyplanner.domain.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface SubjectRepository extends JpaRepository<Subject, UUID> {
    List<Subject> findAllByStudentIdOrderByNameAsc(UUID studentId);
    Optional<Subject> findByIdAndStudentId(UUID id, UUID studentId);
}

