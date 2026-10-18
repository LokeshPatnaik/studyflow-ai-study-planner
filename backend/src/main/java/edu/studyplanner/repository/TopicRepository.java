package edu.studyplanner.repository;
import edu.studyplanner.domain.SyllabusTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface TopicRepository extends JpaRepository<SyllabusTopic, UUID> {
    List<SyllabusTopic> findAllBySubjectIdOrderByTitleAsc(UUID subjectId);
    List<SyllabusTopic> findAllBySubjectIdInAndApprovedTrueAndIncludedTrue(List<UUID> subjectIds);
    Optional<SyllabusTopic> findByIdAndSubjectId(UUID id, UUID subjectId);
}

