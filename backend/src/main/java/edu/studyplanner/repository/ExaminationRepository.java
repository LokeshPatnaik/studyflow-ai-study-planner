package edu.studyplanner.repository;
import edu.studyplanner.domain.Examination;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface ExaminationRepository extends JpaRepository<Examination, UUID> {
    List<Examination> findAllByStudentIdOrderByExamDateAsc(UUID studentId);
    Optional<Examination> findFirstByStudentIdAndSubjectIdOrderByExamDateAsc(UUID studentId, UUID subjectId);
    Optional<Examination> findByIdAndStudentId(UUID id, UUID studentId);
}

