package edu.studyplanner.repository;
import edu.studyplanner.domain.StudentPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface PreferenceRepository extends JpaRepository<StudentPreference, UUID> {}

