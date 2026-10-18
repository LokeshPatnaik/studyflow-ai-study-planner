package edu.studyplanner.repository;
import edu.studyplanner.domain.StudyPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface StudyPlanRepository extends JpaRepository<StudyPlan, UUID> {
    List<StudyPlan> findAllByStudentIdOrderByCreatedAtDesc(UUID studentId);
    Optional<StudyPlan> findFirstByStudentIdAndStatusOrderByCreatedAtDesc(UUID studentId, String status);
    Optional<StudyPlan> findByIdAndStudentId(UUID id, UUID studentId);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from StudyPlan p where p.id = :planId and p.studentId = :studentId")
    Optional<StudyPlan> findByIdAndStudentIdForUpdate(@Param("planId") UUID planId, @Param("studentId") UUID studentId);
}

