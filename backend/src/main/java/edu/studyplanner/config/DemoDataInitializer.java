package edu.studyplanner.config;

import edu.studyplanner.domain.*;
import edu.studyplanner.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.List;

@Component
public class DemoDataInitializer implements CommandLineRunner {
    private final StudentRepository students; private final PreferenceRepository preferences; private final SubjectRepository subjects;
    private final TopicRepository topics; private final ExaminationRepository exams; private final StudyPlanRepository plans;
    private final StudyTaskRepository tasks; private final PasswordEncoder encoder; private final boolean enabled;
    public DemoDataInitializer(StudentRepository students, PreferenceRepository preferences, SubjectRepository subjects,
                               TopicRepository topics, ExaminationRepository exams, StudyPlanRepository plans,
                               StudyTaskRepository tasks, PasswordEncoder encoder, @Value("${app.demo-mode:false}") boolean enabled) {
        this.students = students; this.preferences = preferences; this.subjects = subjects; this.topics = topics; this.exams = exams;
        this.plans = plans; this.tasks = tasks; this.encoder = encoder; this.enabled = enabled;
    }
    @Override @Transactional public void run(String... args) {
        if (!enabled || students.existsByEmailIgnoreCase("demo@planner.local")) return;
        Student demo = students.save(new Student("Alex Morgan", "demo@planner.local", encoder.encode("DemoPass123!"), true));
        StudentPreference pref = new StudentPreference(demo.id); pref.dailyStudyMinutes = 120; pref.sessionMinutes = 50; pref.breakMinutes = 10;
        pref.dayStart = LocalTime.of(17, 0); pref.dayEnd = LocalTime.of(21, 0); preferences.save(pref);
        Subject algorithms = subjects.save(new Subject(demo.id, "Algorithms", "#9b8cff", 5));
        Subject databases = subjects.save(new Subject(demo.id, "Database Systems", "#57c8d2", 4));
        Subject networks = subjects.save(new Subject(demo.id, "Computer Networks", "#f2b86b", 3));
        exams.save(new Examination(demo.id, algorithms.id, "Algorithms midterm", LocalDate.now().plusDays(8)));
        exams.save(new Examination(demo.id, databases.id, "Database systems midterm", LocalDate.now().plusDays(13)));
        exams.save(new Examination(demo.id, networks.id, "Computer networks midterm", LocalDate.now().plusDays(18)));
        SyllabusTopic a1 = topics.save(new SyllabusTopic(algorithms.id, "Graph traversal & shortest paths", "Unit 3", "HARD", 100, true));
        SyllabusTopic a2 = topics.save(new SyllabusTopic(algorithms.id, "Dynamic programming patterns", "Unit 4", "HARD", 100, true));
        topics.save(new SyllabusTopic(algorithms.id, "Greedy algorithms", "Unit 4", "MEDIUM", 60, true));
        SyllabusTopic d1 = topics.save(new SyllabusTopic(databases.id, "Normalization & functional dependencies", "Unit 2", "MEDIUM", 60, true));
        topics.save(new SyllabusTopic(databases.id, "Transactions and concurrency", "Unit 3", "HARD", 90, true));
        topics.save(new SyllabusTopic(networks.id, "Transport layer protocols", "Unit 3", "MEDIUM", 60, true));
        topics.save(new SyllabusTopic(networks.id, "Routing algorithms", "Unit 4", "HARD", 80, true));
        StudyPlan plan = plans.save(new StudyPlan(demo.id, "Midterm momentum", "ACTIVE", "RULE_BASED", "Demo plan created by the local seed. Progress and tasks are sample data."));
        LocalDate today = LocalDate.now();
        tasks.save(new StudyTask(plan.id, demo.id, algorithms.id, a1.id, "Graph traversal · review", "Trace BFS and DFS on a weighted graph.", today.minusDays(1), LocalTime.of(17,0), LocalTime.of(17,50), 50, "HIGH", "COMPLETED", LocalDate.now().plusDays(8)));
        tasks.save(new StudyTask(plan.id, demo.id, databases.id, d1.id, "Functional dependencies · practice", "Find candidate keys and normalize a relation.", today.minusDays(1), LocalTime.of(18,0), LocalTime.of(18,50), 50, "MEDIUM", "MISSED", LocalDate.now().plusDays(13)));
        tasks.save(new StudyTask(plan.id, demo.id, algorithms.id, a2.id, "Dynamic programming · patterns", "Work through recurrence design and tabulation.", today, LocalTime.of(17,0), LocalTime.of(17,50), 50, "HIGH", "PLANNED", LocalDate.now().plusDays(8)));
        tasks.save(new StudyTask(plan.id, demo.id, networks.id, null, "Transport layer · concept map", "Compare TCP flow control and congestion control.", today.plusDays(1), LocalTime.of(17,0), LocalTime.of(17,50), 50, "MEDIUM", "PLANNED", LocalDate.now().plusDays(18)));
        tasks.save(new StudyTask(plan.id, demo.id, databases.id, null, "Transactions · quick recall", "Review ACID, schedules, and serializability.", today.plusDays(2), LocalTime.of(17,0), LocalTime.of(17,50), 50, "HIGH", "PLANNED", LocalDate.now().plusDays(13)));
    }
}

