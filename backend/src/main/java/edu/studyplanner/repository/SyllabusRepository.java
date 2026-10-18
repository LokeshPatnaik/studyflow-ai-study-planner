package edu.studyplanner.repository;
import edu.studyplanner.domain.Syllabus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface SyllabusRepository extends JpaRepository<Syllabus, UUID> {
    List<Syllabus> findAllByStudentIdOrderByCreatedAtDesc(UUID studentId);
}

