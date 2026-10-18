package edu.studyplanner.repository;
import edu.studyplanner.domain.ToolExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface ToolExecutionRepository extends JpaRepository<ToolExecution, UUID> {
    List<ToolExecution> findTop50ByStudentIdOrderByCreatedAtDesc(UUID studentId);
    List<ToolExecution> findAllByRunIdOrderByCreatedAtAsc(UUID runId);
}

