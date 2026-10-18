package edu.studyplanner.api;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import edu.studyplanner.domain.Student;
import edu.studyplanner.domain.Subject;
import edu.studyplanner.domain.SyllabusTopic;
import edu.studyplanner.domain.Syllabus;
import edu.studyplanner.domain.Examination;
import edu.studyplanner.domain.StudyPlan;
import edu.studyplanner.domain.StudyTask;
import edu.studyplanner.domain.ScheduleRevision;
import edu.studyplanner.api.ApiModels.ProposedTask;
import edu.studyplanner.repository.StudentRepository;
import edu.studyplanner.repository.SubjectRepository;
import edu.studyplanner.repository.TopicRepository;
import edu.studyplanner.repository.SyllabusRepository;
import edu.studyplanner.repository.ExaminationRepository;
import edu.studyplanner.repository.StudyPlanRepository;
import edu.studyplanner.repository.StudyTaskRepository;
import edu.studyplanner.repository.RevisionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.demo-mode=true",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.datasource.url=jdbc:h2:mem:planner;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;INIT=CREATE DOMAIN IF NOT EXISTS TIMESTAMPTZ AS TIMESTAMP WITH TIME ZONE"
})
class StudentIsolationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired StudentRepository students;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired SubjectRepository subjectRepository;
    @Autowired TopicRepository topicRepository;
    @Autowired SyllabusRepository syllabusRepository;
    @Autowired ExaminationRepository examinationRepository;
    @Autowired StudyPlanRepository studyPlanRepository;
    @Autowired StudyTaskRepository studyTaskRepository;
    @Autowired RevisionRepository revisionRepository;
    @Autowired ObjectMapper objectMapper;

    @Test void registrationCreatesHashedAccountAndAuthenticatedSessionThatSurvivesRefreshAndLogout() throws Exception {
        String email = "csrf-" + java.util.UUID.randomUUID().toString().substring(0, 8) + "@example.test";
        CsrfState csrf = csrf(null);
        Cookie csrfCookie = csrf.cookie();
        assertThat(csrfCookie).isNotNull();
        String token = csrfCookie.getValue();
        MockHttpSession preAuthSession = new MockHttpSession();
        String oldSessionId = preAuthSession.getId();
        MvcResult registration = mvc.perform(post("/api/v1/auth/register").session(preAuthSession).cookie(csrfCookie).header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"CSRF Student\",\"email\":\"" + email + "\",\"password\":\"StrongPass123!\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.student.email").value(email)).andReturn();
        MockHttpSession session = (MockHttpSession) registration.getRequest().getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getId()).isNotEqualTo(oldSessionId);
        Student stored = students.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(stored.passwordHash).isNotEqualTo("StrongPass123!");
        assertThat(passwordEncoder.matches("StrongPass123!", stored.passwordHash)).isTrue();

        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.student.email").value(email));
        mvc.perform(post("/api/v1/auth/logout").session(session).cookie(csrfCookie).header("X-XSRF-TOKEN", token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());

        MvcResult login = mvc.perform(post("/api/v1/auth/login").cookie(csrfCookie).header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"StrongPass123!\"}"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession restored = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(restored).isNotNull();
        mvc.perform(get("/api/v1/auth/me").session(restored)).andExpect(status().isOk()).andExpect(jsonPath("$.student.email").value(email));
        mvc.perform(post("/api/v1/auth/login").cookie(csrfCookie).header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"IncorrectPass123!\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value("Email or password is incorrect"));

    }

    @Test void invalidDuplicateAndOverlongRegistrationRequestsReturnUsefulValidationProblems() throws Exception {
        String email = "duplicate-" + java.util.UUID.randomUUID().toString().substring(0, 8) + "@example.test";
        CsrfState csrf = csrf(null);
        mvc.perform(post("/api/v1/auth/register").cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.cookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Student\",\"email\":\"" + email + "\",\"password\":\"StrongPass123!\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/auth/register").cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.cookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Student\",\"email\":\"" + email.toUpperCase(java.util.Locale.ROOT) + "\",\"password\":\"StrongPass123!\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("An account with this email already exists"));
        String invalidEmail = "invalid-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        mvc.perform(post("/api/v1/auth/register").cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.cookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Student\",\"email\":\"" + invalidEmail + "\",\"password\":\"StrongPass123!\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("email")));
        String longPasswordEmail = "long-pass-" + java.util.UUID.randomUUID().toString().substring(0, 8) + "@example.test";
        mvc.perform(post("/api/v1/auth/register").cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.cookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Student\",\"email\":\"" + longPasswordEmail + "\",\"password\":\"" + "A".repeat(73) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("Password must be no more than 72 UTF-8 bytes"));
        assertThat(students.findByEmailIgnoreCase(longPasswordEmail)).isEmpty();
    }

    @Test void demoAccountIsSeededAndCanAuthenticate() throws Exception {
        CsrfState csrf = csrf(null);
        MvcResult login = mvc.perform(post("/api/v1/auth/login").cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.cookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"demo@planner.local\",\"password\":\"DemoPass123!\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.student.demo").value(true)).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.student.email").value("demo@planner.local"))
                .andExpect(jsonPath("$.student.demo").value(true));
        mvc.perform(get("/api/v1/subjects").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Algorithms"));
        mvc.perform(get("/api/v1/tasks").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$[0].title").exists());
        mvc.perform(get("/api/v1/plans/active").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(get("/api/v1/plans").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").exists());
        mvc.perform(get("/api/v1/agent/activity").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/revisions").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/analytics").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(5));
    }

    @Test void invalidCsrfHeaderIsRejectedAndGuestMeReturnsUnauthorized() throws Exception {
        CsrfState csrf = csrf(null);
        mvc.perform(post("/api/v1/auth/register").cookie(csrf.cookie()).header("X-XSRF-TOKEN", "not-the-cookie-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Rejected\",\"email\":\"rejected@example.test\",\"password\":\"StrongPass123!\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test void localLoopbackOriginAliasCanLoginAndUnconfiguredOriginsRemainRejected() throws Exception {
        String localOrigin = "http://127.0.0.1:5173";
        mvc.perform(options("/api/v1/auth/login")
                        .header("Origin", localOrigin)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,x-xsrf-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", localOrigin))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));

        CsrfState csrf = csrf(null);
        mvc.perform(post("/api/v1/auth/login").cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.cookie().getValue())
                        .header("Origin", localOrigin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"demo@planner.local\",\"password\":\"DemoPass123!\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", localOrigin));

        mvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test void missingActivePlanIsAValidEmptyResponseForANewStudent() throws Exception {
        MockHttpSession session = registerAndLogin("no-plan-" + java.util.UUID.randomUUID().toString().substring(0, 8) + "@example.test", "New Student");
        mvc.perform(get("/api/v1/plans/active").session(session))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
    }

    @Test void subjectsSyllabusPlanApprovalTasksAndAnalyticsPersistAcrossRequests() throws Exception {
        MockHttpSession session = registerAndLogin("workflow-" + java.util.UUID.randomUUID().toString().substring(0, 8) + "@example.test", "Workflow Student");
        CsrfState csrf = csrf(session);
        MvcResult subjectResult = mvc.perform(post("/api/v1/subjects").session(session).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.cookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Operating Systems\",\"color\":\"#9b8cff\",\"priority\":5,\"examName\":\"Midterm\",\"examDate\":\"" + java.time.LocalDate.now().plusDays(5) + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        String subjectId = com.jayway.jsonpath.JsonPath.read(subjectResult.getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/api/v1/syllabus/manual").session(session).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.cookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":\"" + subjectId + "\",\"source\":\"MANUAL\",\"topics\":[{\"title\":\"Process scheduling\",\"unit\":\"Unit 2\",\"difficulty\":\"MEDIUM\",\"estimatedMinutes\":50}]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$[0].title").value("Process scheduling"));
        mvc.perform(get("/api/v1/syllabus").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$[0].topicCount").value(1));

        MvcResult generated = mvc.perform(post("/api/v1/plans/generate").session(session).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.cookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"OS review\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.tasks[0].status").value("PROPOSED")).andReturn();
        String planId = com.jayway.jsonpath.JsonPath.read(generated.getResponse().getContentAsString(), "$.id");
        String taskId = com.jayway.jsonpath.JsonPath.read(generated.getResponse().getContentAsString(), "$.tasks[0].id");
        mvc.perform(get("/api/v1/plans").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(planId));
        mvc.perform(post("/api/v1/plans/" + planId + "/approve").session(session).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.cookie().getValue()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(patch("/api/v1/tasks/" + taskId + "/status").session(session).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.cookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(get("/api/v1/tasks?status=COMPLETED").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(taskId));
        MvcResult listedTasks = mvc.perform(get("/api/v1/tasks").session(session)).andExpect(status().isOk()).andReturn();
        int totalTasks = com.jayway.jsonpath.JsonPath.read(listedTasks.getResponse().getContentAsString(), "$.length()");
        mvc.perform(get("/api/v1/analytics").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(totalTasks)).andExpect(jsonPath("$.completed").value(1))
                .andExpect(jsonPath("$.pending").value(totalTasks - 1)).andExpect(jsonPath("$.completionPercent").value(Math.round(100f / totalTasks)));
    }

    @Test void eachStudentCanOnlyReadTheirOwnSubjects() throws Exception {
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        MockHttpSession first = registerAndLogin("first-" + suffix + "@example.test", "First Student");
        MockHttpSession second = registerAndLogin("second-" + suffix + "@example.test", "Second Student");
        CsrfState csrf = csrf(first);

        mvc.perform(post("/api/v1/subjects").session(first).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.cookie().getValue()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Algorithms","color":"#9b8cff","priority":5,"examName":"Midterm","examDate":"2027-01-20"}
                                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("Algorithms"));

        mvc.perform(get("/api/v1/subjects").session(first)).andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Algorithms"));
        mvc.perform(get("/api/v1/subjects").session(second)).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test void editedTopicsCanBeExcludedAndLearningCompletionCreatesUniqueSpacedReviews() throws Exception {
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        MockHttpSession session = registerAndLogin("workflow-" + suffix + "@example.test", "Workflow Student");
        CsrfState csrf = csrf(session);
        String subjectBody = "{\"name\":\"Algorithms\",\"color\":\"#9b8cff\",\"priority\":3,\"examName\":\"Final\",\"examDate\":\"" + java.time.LocalDate.now().plusDays(12) + "\"}";
        MvcResult subjectResult = mvc.perform(post("/api/v1/subjects").session(session).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.cookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(subjectBody))
                .andExpect(status().isCreated()).andReturn();
        String subjectId = com.jayway.jsonpath.JsonPath.read(subjectResult.getResponse().getContentAsString(), "$.id");
        MvcResult topicResult = mvc.perform(post("/api/v1/syllabus/manual").session(session).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.cookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"subjectId\":\"" + subjectId + "\",\"topics\":[{\"title\":\"Graph traversal\",\"unit\":\"Unit 1\",\"difficulty\":\"MEDIUM\",\"estimatedMinutes\":50}]}"))
                .andExpect(status().isCreated()).andReturn();
        String topicId = com.jayway.jsonpath.JsonPath.read(topicResult.getResponse().getContentAsString(), "$[0].id");
        int initialEstimate = com.jayway.jsonpath.JsonPath.read(topicResult.getResponse().getContentAsString(), "$[0].estimatedMinutes");
        assertThat(initialEstimate).isBetween(10, 20);
        String topicBody = "{\"subjectId\":\"" + subjectId + "\",\"title\":\"Graph traversal\",\"unit\":\"Unit 1\",\"chapter\":\"Search\",\"description\":\"BFS and DFS\",\"difficulty\":\"HARD\",\"estimatedMinutes\":50,\"importance\":5,\"familiarity\":2,\"included\":false}";
        mvc.perform(patch("/api/v1/subjects/" + subjectId + "/topics/" + topicId).session(session).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.cookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(topicBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.included").value(false)).andExpect(jsonPath("$.originalTitle").value("Graph traversal"))
                .andExpect(jsonPath("$.estimatedMinutes").value(initialEstimate));
        mvc.perform(get("/api/v1/subjects/" + subjectId + "/topics").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].chapter").value("Search")).andExpect(jsonPath("$[0].estimatedMinutes").value(initialEstimate));
        SyllabusTopic storedTopic = topicRepository.findById(UUID.fromString(topicId)).orElseThrow();
        storedTopic.estimatedMinutes = 10;
        topicRepository.saveAndFlush(storedTopic);
        mvc.perform(get("/api/v1/subjects/" + subjectId + "/topics").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].estimatedMinutes").value(10));
        topicBody = topicBody.replace("\"included\":false", "\"included\":true");
        mvc.perform(patch("/api/v1/subjects/" + subjectId + "/topics/" + topicId).session(session).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.cookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(topicBody)).andExpect(status().isOk()).andExpect(jsonPath("$.estimatedMinutes").value(10));

        MvcResult generated = mvc.perform(post("/api/v1/plans/generate").session(session).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.cookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Workflow plan\",\"subjectIds\":[\"" + subjectId + "\"]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.feasible").value(true))
                .andExpect(jsonPath("$.workload.estimatedMinutes").value(10))
                .andExpect(jsonPath("$.tasks[0].durationMinutes").value(10)).andReturn();
        String planId = com.jayway.jsonpath.JsonPath.read(generated.getResponse().getContentAsString(), "$.id");
        java.util.List<String> taskIds = com.jayway.jsonpath.JsonPath.read(generated.getResponse().getContentAsString(), "$.tasks[?(@.sessionType == 'LEARN')].id");
        java.util.List<String> allTaskIds = com.jayway.jsonpath.JsonPath.read(generated.getResponse().getContentAsString(), "$.tasks[*].id");
        String taskId = allTaskIds.get(0);
        String learningTaskId = taskIds.get(0);
        Integer durationMinutes = com.jayway.jsonpath.JsonPath.read(generated.getResponse().getContentAsString(), "$.tasks[0].durationMinutes");
        int validMoveDurationMinutes = Math.max(15, durationMinutes);
        String startTime = com.jayway.jsonpath.JsonPath.read(generated.getResponse().getContentAsString(), "$.tasks[0].startTime");
        mvc.perform(post("/api/v1/plans/" + planId + "/approve").session(session).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.cookie().getValue()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        String scheduledDate = com.jayway.jsonpath.JsonPath.read(generated.getResponse().getContentAsString(), "$.tasks[0].scheduledDate");
        mvc.perform(patch("/api/v1/tasks/" + taskId + "/schedule").session(session).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.cookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"scheduledDate\":\"" + scheduledDate + "\",\"startTime\":\"" + startTime + "\",\"durationMinutes\":" + validMoveDurationMinutes + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.scheduledDate").value(scheduledDate));
        for (String learningId : taskIds) mvc.perform(patch("/api/v1/tasks/" + learningId + "/status").session(session).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.cookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessionType").value("LEARN"));
        mvc.perform(get("/api/v1/tasks").session(session).param("status", "PLANNED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.sessionType == 'REVISION')]", org.hamcrest.Matchers.hasSize(3)));
        mvc.perform(patch("/api/v1/tasks/" + learningTaskId + "/status").session(session).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.cookie().getValue())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/tasks").session(session).param("status", "PLANNED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.sessionType == 'REVISION')]", org.hamcrest.Matchers.hasSize(3)));
    }

    @Test void deletingSubjectRemovesItsSyllabusAndSessionsButPreservesOtherPlanWork() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        MockHttpSession session = registerAndLogin("delete-subject-" + suffix + "@example.test", "Delete Subject Student");
        CsrfState csrf = csrf(session);
        Student student = students.findByEmailIgnoreCase("delete-subject-" + suffix + "@example.test").orElseThrow();
        Subject removed = saveSubject(student.id, "Networks");
        Subject retained = saveSubject(student.id, "Databases");
        SyllabusTopic removedTopic = topicRepository.save(new SyllabusTopic(removed.id, "Routing", "Unit 1", "MEDIUM", 60, true));
        SyllabusTopic retainedTopic = topicRepository.save(new SyllabusTopic(retained.id, "Indexes", "Unit 2", "MEDIUM", 60, true));
        syllabusRepository.save(new Syllabus(student.id, removed.id, "Networks.pdf", "PDF", 1));
        syllabusRepository.save(new Syllabus(student.id, retained.id, "Databases.pdf", "PDF", 1));
        examinationRepository.save(new Examination(student.id, removed.id, "Networks final", LocalDate.now().plusDays(10)));
        examinationRepository.save(new Examination(student.id, retained.id, "Database final", LocalDate.now().plusDays(12)));
        StudyPlan plan = studyPlanRepository.save(new StudyPlan(student.id, "Combined exam plan", "ACTIVE", "RULE_BASED", "Two subjects"));
        StudyTask removedTask = saveTask(student.id, plan.id, removed, removedTopic, "LEARN");
        saveTask(student.id, plan.id, removed, removedTopic, "REVISION");
        StudyTask retainedTask = saveTask(student.id, plan.id, retained, retainedTopic, "LEARN");
        ScheduleRevision mixed = saveRevision(student.id, plan.id, List.of(proposal(removedTask, removed), proposal(retainedTask, retained)));
        ScheduleRevision subjectOnly = saveRevision(student.id, plan.id, List.of(proposal(removedTask, removed)));

        mvc.perform(delete("/api/v1/subjects/" + removed.id).session(session).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.cookie().getValue()))
                .andExpect(status().isNoContent());

        assertThat(subjectRepository.findById(removed.id)).isEmpty();
        assertThat(students.findById(student.id)).isPresent();
        assertThat(topicRepository.findAllBySubjectIdOrderByTitleAsc(removed.id)).isEmpty();
        assertThat(syllabusRepository.findAllByStudentIdOrderByCreatedAtDesc(student.id).stream().filter(s -> s.subjectId.equals(removed.id))).isEmpty();
        assertThat(examinationRepository.findAllByStudentIdOrderByExamDateAsc(student.id).stream().filter(e -> e.subjectId.equals(removed.id))).isEmpty();
        assertThat(subjectRepository.findById(retained.id)).isPresent();
        assertThat(studyPlanRepository.findById(plan.id)).isPresent();
        assertThat(studyTaskRepository.findAllByPlanIdOrderByScheduledDateAscStartTimeAsc(plan.id).stream().map(t -> t.id))
                .containsExactly(retainedTask.id);
        ScheduleRevision savedMixed = revisionRepository.findByIdAndStudentId(mixed.id, student.id).orElseThrow();
        List<ProposedTask> retainedProposals = objectMapper.readValue(savedMixed.proposedJson, new TypeReference<>() {});
        assertThat(retainedProposals).extracting(ProposedTask::subjectId).containsExactly(retained.id);
        ScheduleRevision savedSubjectOnly = revisionRepository.findByIdAndStudentId(subjectOnly.id, student.id).orElseThrow();
        assertThat(savedSubjectOnly.status).isEqualTo("REJECTED");
        assertThat(objectMapper.readValue(savedSubjectOnly.proposedJson, new TypeReference<List<ProposedTask>>() {})).isEmpty();
    }

    @Test void deletingPlanCascadesTasksAndRevisionsButKeepsSubjectsAndTopics() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        MockHttpSession session = registerAndLogin("delete-plan-" + suffix + "@example.test", "Delete Plan Student");
        CsrfState csrf = csrf(session);
        Student student = students.findByEmailIgnoreCase("delete-plan-" + suffix + "@example.test").orElseThrow();
        Subject subject = saveSubject(student.id, "Operating Systems");
        SyllabusTopic topic = topicRepository.save(new SyllabusTopic(subject.id, "Scheduling", "Unit 1", "MEDIUM", 60, true));
        StudyPlan plan = studyPlanRepository.save(new StudyPlan(student.id, "OS review", "ACTIVE", "RULE_BASED", "Review"));
        StudyTask task = saveTask(student.id, plan.id, subject, topic, "REVISION");
        ScheduleRevision revision = saveRevision(student.id, plan.id, List.of(proposal(task, subject)));
        StudyPlan retainedPlan = studyPlanRepository.save(new StudyPlan(student.id, "Separate math plan", "PAUSED", "RULE_BASED", "Keep this plan"));
        StudyTask retainedTask = saveTask(student.id, retainedPlan.id, subject, topic, "LEARN");
        ScheduleRevision retainedRevision = saveRevision(student.id, retainedPlan.id, List.of(proposal(retainedTask, subject)));

        mvc.perform(delete("/api/v1/plans/" + plan.id).session(session).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.cookie().getValue()))
                .andExpect(status().isNoContent());

        assertThat(studyPlanRepository.findById(plan.id)).isEmpty();
        assertThat(studyTaskRepository.findAllByPlanIdOrderByScheduledDateAscStartTimeAsc(plan.id)).isEmpty();
        assertThat(revisionRepository.findByIdAndStudentId(revision.id, student.id)).isEmpty();
        assertThat(subjectRepository.findById(subject.id)).isPresent();
        assertThat(topicRepository.findById(topic.id)).isPresent();
        assertThat(students.findById(student.id)).isPresent();
        assertThat(studyPlanRepository.findById(retainedPlan.id)).isPresent();
        assertThat(studyTaskRepository.findByIdAndStudentId(retainedTask.id, student.id)).isPresent();
        assertThat(revisionRepository.findByIdAndStudentId(retainedRevision.id, student.id)).isPresent();
    }

    @Test void deleteEndpointsReturnNotFoundForMissingOrAnotherStudentsRecords() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        MockHttpSession owner = registerAndLogin("owner-delete-" + suffix + "@example.test", "Owner");
        MockHttpSession other = registerAndLogin("other-delete-" + suffix + "@example.test", "Other");
        CsrfState ownerCsrf = csrf(owner);
        CsrfState otherCsrf = csrf(other);
        Student ownerStudent = students.findByEmailIgnoreCase("owner-delete-" + suffix + "@example.test").orElseThrow();
        Subject subject = saveSubject(ownerStudent.id, "Private subject");
        SyllabusTopic topic = topicRepository.save(new SyllabusTopic(subject.id, "Private topic", "Unit 1", "MEDIUM", 60, true));
        StudyPlan plan = studyPlanRepository.save(new StudyPlan(ownerStudent.id, "Private plan", "ACTIVE", "RULE_BASED", "Private"));
        StudyTask task = saveTask(ownerStudent.id, plan.id, subject, topic, "REVISION");
        ScheduleRevision revision = saveRevision(ownerStudent.id, plan.id, List.of(proposal(task, subject)));

        mvc.perform(delete("/api/v1/subjects/" + UUID.randomUUID()).session(owner).cookie(ownerCsrf.cookie()).header("X-XSRF-TOKEN", ownerCsrf.cookie().getValue())).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/plans/" + UUID.randomUUID()).session(owner).cookie(ownerCsrf.cookie()).header("X-XSRF-TOKEN", ownerCsrf.cookie().getValue())).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/subjects/" + subject.id).session(other).cookie(otherCsrf.cookie()).header("X-XSRF-TOKEN", otherCsrf.cookie().getValue())).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/plans/" + plan.id).session(other).cookie(otherCsrf.cookie()).header("X-XSRF-TOKEN", otherCsrf.cookie().getValue())).andExpect(status().isNotFound());
        CsrfState guestCsrf = csrf(null);
        mvc.perform(delete("/api/v1/subjects/" + subject.id).cookie(guestCsrf.cookie()).header("X-XSRF-TOKEN", guestCsrf.cookie().getValue())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/plans/" + plan.id).cookie(guestCsrf.cookie()).header("X-XSRF-TOKEN", guestCsrf.cookie().getValue())).andExpect(status().isForbidden());

        assertThat(subjectRepository.findById(subject.id)).isPresent();
        assertThat(studyPlanRepository.findById(plan.id)).isPresent();
        assertThat(studyTaskRepository.findByIdAndStudentId(task.id, ownerStudent.id)).isPresent();
        assertThat(revisionRepository.findByIdAndStudentId(revision.id, ownerStudent.id)).isPresent();
    }

    private Subject saveSubject(UUID studentId, String name) {
        return subjectRepository.save(new Subject(studentId, name, "#9182f5", 3));
    }

    private StudyTask saveTask(UUID studentId, UUID planId, Subject subject, SyllabusTopic topic, String sessionType) {
        StudyTask task = new StudyTask(planId, studentId, subject.id, topic.id, topic.title, "Study session",
                LocalDate.now().plusDays(2), LocalTime.of(10, 0), LocalTime.of(11, 0), 60, "HIGH", "PLANNED", LocalDate.now().plusDays(10));
        task.sessionType = sessionType;
        return studyTaskRepository.save(task);
    }

    private ProposedTask proposal(StudyTask task, Subject subject) {
        return new ProposedTask(task.id, subject.id, task.topicId, subject.name, task.title, task.description,
                task.scheduledDate, task.startTime, task.endTime, task.durationMinutes, task.priority, task.examDate);
    }

    private ScheduleRevision saveRevision(UUID studentId, UUID planId, List<ProposedTask> proposal) throws Exception {
        return revisionRepository.save(new ScheduleRevision(studentId, planId, "Move sessions around a commitment.", objectMapper.writeValueAsString(proposal)));
    }

    private MockHttpSession registerAndLogin(String email, String name) throws Exception {
        CsrfState csrf = csrf(null);
        mvc.perform(post("/api/v1/auth/register").cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.cookie().getValue()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"StrongPass123!\"}"))
                .andExpect(status().isCreated());
        MvcResult result = mvc.perform(post("/api/v1/auth/login").cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.cookie().getValue()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"StrongPass123!\"}"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    private CsrfState csrf(MockHttpSession session) throws Exception {
        var request = get("/api/v1/auth/csrf");
        if (session != null) request.session(session);
        MvcResult result = mvc.perform(request).andExpect(status().isOk())
                .andExpect(jsonPath("$.ready").value(true)).andReturn();
        return new CsrfState(result.getResponse().getCookie("XSRF-TOKEN"));
    }

    private record CsrfState(Cookie cookie) {}
}
