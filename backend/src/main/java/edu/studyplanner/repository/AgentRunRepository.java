package edu.studyplanner.repository;
import edu.studyplanner.domain.AgentRun;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface AgentRunRepository extends JpaRepository<AgentRun, UUID> {
    List<AgentRun> findTop30ByStudentIdOrderByCreatedAtDesc(UUID studentId);
}

