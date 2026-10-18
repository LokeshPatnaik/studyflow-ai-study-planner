package edu.studyplanner.repository;
import edu.studyplanner.domain.StudyTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface StudyTaskRepository extends JpaRepository<StudyTask, UUID> {
    List<StudyTask> findAllByStudentIdOrderByScheduledDateAscStartTimeAsc(UUID studentId);
    List<StudyTask> findAllByPlanIdOrderByScheduledDateAscStartTimeAsc(UUID planId);
    List<StudyTask> findAllByStudentIdAndPlanIdOrderByScheduledDateAscStartTimeAsc(UUID studentId, UUID planId);
    Optional<StudyTask> findByIdAndStudentId(UUID id, UUID studentId);
    List<StudyTask> findAllByPlanIdAndTopicIdAndSessionType(UUID planId, UUID topicId, String sessionType);
    long countByStudentIdAndStatus(UUID studentId, String status);
}

