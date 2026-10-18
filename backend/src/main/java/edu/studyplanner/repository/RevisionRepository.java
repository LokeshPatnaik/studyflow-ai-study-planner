package edu.studyplanner.repository;
import edu.studyplanner.domain.ScheduleRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface RevisionRepository extends JpaRepository<ScheduleRevision, UUID> {
    List<ScheduleRevision> findAllByStudentIdOrderByCreatedAtDesc(UUID studentId);
    Optional<ScheduleRevision> findByIdAndStudentId(UUID id, UUID studentId);
}

