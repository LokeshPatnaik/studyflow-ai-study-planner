package edu.studyplanner.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.studyplanner.api.ApiModels.PlanDto;
import edu.studyplanner.agent.SyllabusTopicParser;
import edu.studyplanner.domain.*;
import edu.studyplanner.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.mock.web.MockMultipartFile;
import edu.studyplanner.api.ApiModels.ParsedTopic;
import edu.studyplanner.api.ApiModels.AddTopicsRequest;
import edu.studyplanner.api.ApiModels.CommitmentRequest;
import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlannerServiceTest {
    @Mock SubjectRepository subjects;
    @Mock TopicRepository topics;
    @Mock ExaminationRepository exams;
    @Mock StudyPlanRepository plans;
    @Mock StudyTaskRepository tasks;
    @Mock RevisionRepository revisions;
    @Mock PreferenceRepository preferences;
    @Mock SyllabusRepository syllabi;
    @Mock SyllabusTopicParser openai;
    @InjectMocks PlannerService planner;
    private final UUID studentId = UUID.randomUUID();

    @BeforeEach void setup() {
        planner = new PlannerService(subjects, topics, exams, plans, tasks, revisions, preferences, syllabi,
                new ObjectMapper().findAndRegisterModules(), openai);
    }

    @Test void estimatedTopicTimeIsRandomlyBoundedFromTenToTwentyMinutes() {
        assertThat(java.util.stream.IntStream.range(0, 100)
                .map(ignored -> planner.estimateTopicDuration("HARD", 5)).boxed().toList())
                .allSatisfy(minutes -> assertThat(minutes).isBetween(10, 20));
    }

    @Test void newTopicsReceiveAndSaveARandomEstimateFromTenToTwentyMinutes() {
        var subject = new Subject(studentId, "Algorithms", "#9b8cff", 4);
        when(subjects.findByIdAndStudentId(subject.id, studentId)).thenReturn(Optional.of(subject));
        when(topics.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var created = planner.addTopics(studentId, new AddTopicsRequest(subject.id, "PDF", "syllabus.pdf",
                List.of(new ParsedTopic("Graph traversal", "Unit 1", "HARD", 120))));

        assertThat(created).singleElement().satisfies(topic -> {
            assertThat(topic.estimatedMinutes()).isBetween(10, 20);
            assertThat(topic.title()).isEqualTo("Graph traversal");
        });
        verify(topics).save(argThat(topic -> topic.estimatedMinutes >= 10 && topic.estimatedMinutes <= 20));
    }

    @Test void usesValidatedAiSyllabusSuggestionsWhenConfigured() throws Exception {
        when(openai.configured()).thenReturn(true);
        when(openai.parseSyllabusTopics(any())).thenReturn(List.of(new ParsedTopic("Graph traversal", "Unit 1", "HARD", 90)));
        var result = planner.parsePdf(new MockMultipartFile("file", "syllabus.pdf", "application/pdf", pdfWithTopics()));
        assertThat(result.parsed()).isTrue();
        assertThat(result.message()).contains("AI provided additional difficulty and time guidance");
        assertThat(result.topics()).hasSize(1).first().satisfies(topic -> {
            assertThat(topic.title()).isEqualTo("Graph traversal");
            assertThat(topic.unit()).isEqualTo("Unit 1");
            assertThat(topic.chapter()).isEqualTo("Algorithms");
            assertThat(topic.difficulty()).isEqualTo("HARD");
            assertThat(topic.estimatedMinutes()).isEqualTo(90);
            assertThat(topic.sourceContent()).anyMatch(source -> source.text().contains("Graph traversal") && source.page() == 1);
        });
        verify(openai).parseSyllabusTopics(any());
    }

    @Test void fallsBackToLocalPdfParsingWhenNoAiKeyIsConfigured() throws Exception {
        when(openai.configured()).thenReturn(false);

        var result = planner.parsePdf(new MockMultipartFile("file", "syllabus.pdf", "application/pdf", pdfWithTopics()));

        assertThat(result.parsed()).isTrue();
        assertThat(result.message()).contains("Local rule-based extraction", "OPENAI_API_KEY");
        assertThat(result.topics()).hasSize(1).first().satisfies(topic -> {
            assertThat(topic.title()).isEqualTo("Graph traversal");
            assertThat(topic.unit()).isEqualTo("Unit 1");
            assertThat(topic.chapter()).isEqualTo("Algorithms");
            assertThat(topic.estimatedMinutes()).isGreaterThanOrEqualTo(45);
            assertThat(topic.sourceContent()).anyMatch(source -> source.text().contains("Graph traversal") && source.page() == 1);
        });
        verify(openai, never()).parseSyllabusTopics(any());
    }

    @Test void aPdfWithSeventyTinyHeadingsIsPresentedAsAboutTenSourceLinkedTopics() throws Exception {
        when(openai.configured()).thenReturn(false);

        var result = planner.parsePdf(new MockMultipartFile("file", "many-topics.pdf", "application/pdf", pdfWithManyTinyTopics()));

        assertThat(result.parsed()).isTrue();
        assertThat(result.topics()).hasSizeBetween(8, 12);
        var originalHeadings = result.topics().stream().flatMap(topic -> topic.sourceContent().stream())
                .filter(source -> source.heading() && source.text().matches("1\\.\\d+ .+"))
                .toList();
        assertThat(originalHeadings).hasSize(70);
        assertThat(originalHeadings).extracting(source -> source.page()).contains(1, 2, 3, 4, 5, 6, 7);
        assertThat(result.topics()).allSatisfy(topic -> assertThat(topic.chapter()).isEqualTo("Algorithms"));
    }

    @Test void fallsBackToLocalPdfParsingWhenTheAiServiceFails() throws Exception {
        when(openai.configured()).thenReturn(true);
        when(openai.parseSyllabusTopics(any())).thenThrow(new IllegalStateException("simulated rate limit"));

        var result = planner.parsePdf(new MockMultipartFile("file", "syllabus.pdf", "application/pdf", pdfWithTopics()));

        assertThat(result.parsed()).isTrue();
        assertThat(result.message()).contains("AI parsing was unavailable", "rule-based");
        assertThat(result.topics()).hasSize(1).first().satisfies(topic -> {
            assertThat(topic.title()).isEqualTo("Graph traversal");
            assertThat(topic.chapter()).isEqualTo("Algorithms");
            assertThat(topic.sourceContent()).anyMatch(source -> source.text().contains("Graph traversal"));
        });
    }

    @Test void createsAFeasibleDraftWithNonOverlappingSessionsBeforeTheExam() {
        var subject = new Subject(studentId, "Algorithms", "#9b8cff", 5);
        var topic = new SyllabusTopic(subject.id, "Graph traversal", "Unit 3", "HARD", 20, true);
        var exam = new Examination(studentId, subject.id, "Midterm", LocalDate.now().plusDays(4));
        var preference = preference();
        when(plans.findFirstByStudentIdAndStatusOrderByCreatedAtDesc(studentId, "DRAFT")).thenReturn(Optional.empty());
        when(subjects.findAllByStudentIdOrderByNameAsc(studentId)).thenReturn(List.of(subject));
        when(topics.findAllBySubjectIdInAndApprovedTrueAndIncludedTrue(List.of(subject.id))).thenReturn(List.of(topic));
        when(exams.findFirstByStudentIdAndSubjectIdOrderByExamDateAsc(studentId, subject.id)).thenReturn(Optional.of(exam));
        when(preferences.findById(studentId)).thenReturn(Optional.of(preference));
        when(tasks.findAllByStudentIdOrderByScheduledDateAscStartTimeAsc(studentId)).thenReturn(List.of());
        when(plans.save(any())).thenAnswer(i -> i.getArgument(0));
        when(tasks.save(any())).thenAnswer(i -> i.getArgument(0));
        when(subjects.findById(subject.id)).thenReturn(Optional.of(subject));

        PlanDto result = planner.generatePlan(studentId, "Algorithms sprint", "RULE_BASED");

        assertThat(result.feasible()).isTrue();
        assertThat(result.id()).isNotNull();
        assertThat(result.status()).isEqualTo("DRAFT");
        assertThat(result.tasks()).isNotEmpty().allSatisfy(t -> {
            assertThat(t.status()).isEqualTo("PROPOSED");
            assertThat(t.scheduledDate()).isBefore(exam.examDate);
        });
        assertThat(result.workload().estimatedMinutes()).isEqualTo(result.workload().topics().get(0).totalMinutes());
        assertThat(result.workload().scheduledMinutes()).isEqualTo(result.workload().estimatedMinutes());
        assertThat(result.tasks()).extracting(t -> t.sessionType()).containsExactly("LEARN");
        assertThat(result.tasks()).singleElement().satisfies(task -> assertThat(task.durationMinutes()).isEqualTo(20));
        assertThat(result.workload().topics()).singleElement().satisfies(topicEstimate -> {
            assertThat(topicEstimate.learningMinutes()).isEqualTo(20);
            assertThat(topicEstimate.practiceMinutes()).isZero();
            assertThat(topicEstimate.revisionMinutes()).isZero();
            assertThat(topicEstimate.totalMinutes()).isEqualTo(20);
        });
        for (int i = 1; i < result.tasks().size(); i++) {
            var previous = result.tasks().get(i - 1);
            var next = result.tasks().get(i);
            assertThat(previous.scheduledDate().isBefore(next.scheduledDate())
                    || (previous.scheduledDate().equals(next.scheduledDate()) && !previous.endTime().isAfter(next.startTime()))).isTrue();
        }
    }

    @Test void reportsAnImpossibleDeadlineWithoutSavingAPlan() {
        var subject = new Subject(studentId, "Algorithms", "#9b8cff", 5);
        var topic = new SyllabusTopic(subject.id, "Graph traversal", "Unit 3", "HARD", 100, true);
        var exam = new Examination(studentId, subject.id, "Midterm", LocalDate.now());
        var preference = preference(); preference.dailyStudyMinutes = 50;
        when(plans.findFirstByStudentIdAndStatusOrderByCreatedAtDesc(studentId, "DRAFT")).thenReturn(Optional.empty());
        when(subjects.findAllByStudentIdOrderByNameAsc(studentId)).thenReturn(List.of(subject));
        when(topics.findAllBySubjectIdInAndApprovedTrueAndIncludedTrue(List.of(subject.id))).thenReturn(List.of(topic));
        when(exams.findFirstByStudentIdAndSubjectIdOrderByExamDateAsc(studentId, subject.id)).thenReturn(Optional.of(exam));
        when(preferences.findById(studentId)).thenReturn(Optional.of(preference));
        when(tasks.findAllByStudentIdOrderByScheduledDateAscStartTimeAsc(studentId)).thenReturn(List.of());

        PlanDto result = planner.generatePlan(studentId, null, "RULE_BASED");

        assertThat(result.feasible()).isFalse();
        assertThat(result.id()).isNull();
        assertThat(result.conflicts()).isNotEmpty();
        assertThat(result.workload().shortfallMinutes()).isGreaterThan(0);
        assertThat(result.workload().topics()).singleElement().satisfies(estimate -> assertThat(estimate.unscheduledMinutes()).isGreaterThan(0));
        verify(plans, never()).save(any());
    }

    @Test void doesNotSchedulePastTheEndOfTheStudyWindowAfterABreak() {
        var subject = new Subject(studentId, "Algorithms", "#9b8cff", 5);
        var topic = new SyllabusTopic(subject.id, "Graph traversal", "Unit 3", "HARD", 100, true);
        var exam = new Examination(studentId, subject.id, "Midterm", LocalDate.now().plusDays(5));
        var preference = preference(); preference.dailyStudyMinutes = 120; preference.breakMinutes = 60;
        preference.dayStart = LocalTime.of(17, 0); preference.dayEnd = LocalTime.of(19, 0);
        when(plans.findFirstByStudentIdAndStatusOrderByCreatedAtDesc(studentId, "DRAFT")).thenReturn(Optional.empty());
        when(subjects.findAllByStudentIdOrderByNameAsc(studentId)).thenReturn(List.of(subject));
        when(topics.findAllBySubjectIdInAndApprovedTrueAndIncludedTrue(List.of(subject.id))).thenReturn(List.of(topic));
        when(exams.findFirstByStudentIdAndSubjectIdOrderByExamDateAsc(studentId, subject.id)).thenReturn(Optional.of(exam));
        when(preferences.findById(studentId)).thenReturn(Optional.of(preference));
        when(tasks.findAllByStudentIdOrderByScheduledDateAscStartTimeAsc(studentId)).thenReturn(List.of());
        when(plans.save(any())).thenAnswer(i -> i.getArgument(0));
        when(tasks.save(any())).thenAnswer(i -> i.getArgument(0));
        when(subjects.findById(subject.id)).thenReturn(Optional.of(subject));

        PlanDto result = planner.generatePlan(studentId, "Algorithms sprint", "RULE_BASED");

        assertThat(result.feasible()).isTrue();
        assertThat(result.tasks()).isNotEmpty().allSatisfy(task -> {
            assertThat(task.startTime()).isAfterOrEqualTo(preference.dayStart);
            assertThat(task.endTime()).isBeforeOrEqualTo(preference.dayEnd);
        });
    }

    @Test void workloadUsesSavedEstimateWithoutMultiplyingLearningPracticeAndRevision() {
        var subject = new Subject(studentId, "Algorithms", "#9b8cff", 4);
        var familiarEasy = new SyllabusTopic(subject.id, "Familiar basics", "Unit 1", "EASY", 20, true);
        familiarEasy.importance = 1; familiarEasy.familiarity = 5;
        var difficultImportant = new SyllabusTopic(subject.id, "Advanced proofs", "Unit 2", "HARD", 10, true);
        difficultImportant.importance = 5; difficultImportant.familiarity = 1;
        LocalDate today = LocalDate.now(java.time.ZoneId.of("UTC"));
        when(plans.findFirstByStudentIdAndStatusOrderByCreatedAtDesc(studentId, "DRAFT")).thenReturn(Optional.empty());
        when(subjects.findAllByStudentIdOrderByNameAsc(studentId)).thenReturn(List.of(subject));
        when(topics.findAllBySubjectIdInAndApprovedTrueAndIncludedTrue(List.of(subject.id))).thenReturn(List.of(familiarEasy, difficultImportant));
        when(exams.findFirstByStudentIdAndSubjectIdOrderByExamDateAsc(studentId, subject.id)).thenReturn(Optional.of(new Examination(studentId, subject.id, "Final", today.plusDays(20))));
        when(preferences.findById(studentId)).thenReturn(Optional.of(preference()));
        when(tasks.findAllByStudentIdOrderByScheduledDateAscStartTimeAsc(studentId)).thenReturn(List.of());
        when(plans.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tasks.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(subjects.findById(subject.id)).thenReturn(Optional.of(subject));

        PlanDto result = planner.generatePlan(studentId, "Estimates", "RULE_BASED", List.of(subject.id), List.of(), List.of(), "UTC");

        var easy = result.workload().topics().stream().filter(topic -> topic.title().equals("Familiar basics")).findFirst().orElseThrow();
        var hard = result.workload().topics().stream().filter(topic -> topic.title().equals("Advanced proofs")).findFirst().orElseThrow();
        assertThat(easy.totalMinutes()).isEqualTo(20);
        assertThat(hard.totalMinutes()).isEqualTo(10);
        assertThat(easy.learningMinutes() + easy.practiceMinutes() + easy.revisionMinutes()).isEqualTo(easy.totalMinutes());
        assertThat(hard.learningMinutes() + hard.practiceMinutes() + hard.revisionMinutes()).isEqualTo(hard.totalMinutes());
        assertThat(result.workload().estimatedMinutes()).isEqualTo(easy.totalMinutes() + hard.totalMinutes());
        assertThat(easy.practiceMinutes()).isZero();
        assertThat(easy.revisionMinutes()).isZero();
    }

    @Test void unavailableDatesAreExcludedFromCapacityAndScheduledSessions() {
        var subject = new Subject(studentId, "Algorithms", "#9b8cff", 4);
        var topic = new SyllabusTopic(subject.id, "Graph traversal", "Unit 1", "MEDIUM", 60, true);
        LocalDate today = LocalDate.now(java.time.ZoneId.of("UTC"));
        LocalDate unavailable = today.plusDays(1);
        when(plans.findFirstByStudentIdAndStatusOrderByCreatedAtDesc(studentId, "DRAFT")).thenReturn(Optional.empty());
        when(subjects.findAllByStudentIdOrderByNameAsc(studentId)).thenReturn(List.of(subject));
        when(topics.findAllBySubjectIdInAndApprovedTrueAndIncludedTrue(List.of(subject.id))).thenReturn(List.of(topic));
        when(exams.findFirstByStudentIdAndSubjectIdOrderByExamDateAsc(studentId, subject.id)).thenReturn(Optional.of(new Examination(studentId, subject.id, "Final", today.plusDays(8))));
        when(preferences.findById(studentId)).thenReturn(Optional.of(preference()));
        when(tasks.findAllByStudentIdOrderByScheduledDateAscStartTimeAsc(studentId)).thenReturn(List.of());
        when(plans.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tasks.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(subjects.findById(subject.id)).thenReturn(Optional.of(subject));

        PlanDto result = planner.generatePlan(studentId, "Holiday aware", "RULE_BASED", List.of(subject.id), List.of(unavailable), List.of(), "UTC");

        assertThat(result.feasible()).isTrue();
        assertThat(result.workload().availableCapacityMinutes()).isLessThan(8 * preference().dailyStudyMinutes);
        assertThat(result.tasks()).allSatisfy(task -> assertThat(task.scheduledDate()).isNotEqualTo(unavailable));
    }

    @Test void existingCommitmentsAreRespectedByTheGeneratedTimetable() {
        var subject = new Subject(studentId, "Algorithms", "#9b8cff", 4);
        var topic = new SyllabusTopic(subject.id, "Graph traversal", "Unit 1", "MEDIUM", 60, true);
        LocalDate today = LocalDate.now(java.time.ZoneId.of("UTC"));
        when(plans.findFirstByStudentIdAndStatusOrderByCreatedAtDesc(studentId, "DRAFT")).thenReturn(Optional.empty());
        when(subjects.findAllByStudentIdOrderByNameAsc(studentId)).thenReturn(List.of(subject));
        when(topics.findAllBySubjectIdInAndApprovedTrueAndIncludedTrue(List.of(subject.id))).thenReturn(List.of(topic));
        when(exams.findFirstByStudentIdAndSubjectIdOrderByExamDateAsc(studentId, subject.id)).thenReturn(Optional.of(new Examination(studentId, subject.id, "Final", today.plusDays(8))));
        when(preferences.findById(studentId)).thenReturn(Optional.of(preference()));
        when(tasks.findAllByStudentIdOrderByScheduledDateAscStartTimeAsc(studentId)).thenReturn(List.of());
        when(plans.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tasks.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(subjects.findById(subject.id)).thenReturn(Optional.of(subject));
        var commitment = new CommitmentRequest(today.plusDays(1), LocalTime.of(17, 0), LocalTime.of(20, 0), "Class");

        PlanDto result = planner.generatePlan(studentId, "Commitments", "RULE_BASED", List.of(subject.id), List.of(), List.of(commitment), "UTC");

        assertThat(result.feasible()).isTrue();
        assertThat(result.tasks()).noneSatisfy(task -> assertThat(task.scheduledDate()).isEqualTo(commitment.date()));
    }

    @Test void rejectsOverlappingCandidateSessionsDuringValidation() {
        var subject = new Subject(studentId, "Algorithms", "#9b8cff", 4);
        var plan = new StudyPlan(studentId, "Candidate", "DRAFT", "RULE_BASED", "Review");
        var date = LocalDate.now().plusDays(1);
        var first = new StudyTask(plan.id, studentId, subject.id, null, "First", "", date, LocalTime.of(17,0), LocalTime.of(18,0), 60, "HIGH", "PROPOSED", date.plusDays(3));
        var second = new StudyTask(plan.id, studentId, subject.id, null, "Second", "", date, LocalTime.of(17,30), LocalTime.of(18,30), 60, "MEDIUM", "PROPOSED", date.plusDays(3));
        when(plans.findByIdAndStudentId(plan.id, studentId)).thenReturn(Optional.of(plan));
        when(tasks.findAllByStudentIdAndPlanIdOrderByScheduledDateAscStartTimeAsc(studentId, plan.id)).thenReturn(List.of(first, second));

        var result = planner.validatePlan(studentId, plan.id);

        assertThat(result.get("feasible")).isEqualTo(false);
        assertThat((List<?>)result.get("conflicts")).anyMatch(x -> x.toString().contains("Overlapping"));
    }

    @Test void completesAnOwnedActiveTaskAndRejectsDraftTaskUpdates() {
        var subject = new Subject(studentId, "Algorithms", "#9b8cff", 4);
        var plan = new StudyPlan(studentId, "Active", "ACTIVE", "RULE_BASED", "");
        var task = new StudyTask(plan.id, studentId, subject.id, null, "Revision", "", LocalDate.now(), LocalTime.of(17,0), LocalTime.of(17,50), 50, "HIGH", "PLANNED", LocalDate.now().plusDays(3));
        when(tasks.findByIdAndStudentId(task.id, studentId)).thenReturn(Optional.of(task));
        when(plans.findByIdAndStudentIdForUpdate(plan.id, studentId)).thenReturn(Optional.of(plan));
        when(tasks.save(task)).thenReturn(task);
        when(subjects.findById(subject.id)).thenReturn(Optional.of(subject));

        var updated = planner.updateTaskStatus(studentId, task.id, "COMPLETED");

        assertThat(updated.status()).isEqualTo("COMPLETED");
        assertThat(task.completedAt).isNotNull();
    }

    private StudentPreference preference() {
        StudentPreference p = new StudentPreference(studentId); p.dailyStudyMinutes = 120; p.sessionMinutes = 50;
        p.breakMinutes = 10; p.preferredDays = "MON,TUE,WED,THU,FRI,SAT,SUN"; p.dayStart = LocalTime.of(17,0); p.dayEnd = LocalTime.of(21,0);
        return p;
    }

    private byte[] pdfWithTopics() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(); document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText(); stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12); stream.newLineAtOffset(50, 750);
                stream.showText("Unit 1: Algorithms"); stream.newLineAtOffset(0, -16); stream.showText("1.1 Graph traversal"); stream.endText();
            }
            document.save(output); return output.toByteArray();
        }
    }

    private byte[] pdfWithManyTinyTopics() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (int pageIndex = 0; pageIndex < 7; pageIndex++) {
                PDPage page = new PDPage(); document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText(); stream.setFont(font, 11); stream.newLineAtOffset(45, 750);
                    if (pageIndex == 0) { stream.showText("Unit 1: Algorithms"); stream.newLineAtOffset(0, -16); }
                    for (int index = pageIndex * 10 + 1; index <= pageIndex * 10 + 10; index++) {
                        String detail = switch ((index - 1) / 10) {
                            case 0 -> "Graph traversal search " + index;
                            case 1 -> "Graph shortest path " + index;
                            case 2 -> "Graph spanning tree " + index;
                            case 3 -> "Tree traversal and search " + index;
                            case 4 -> "Dynamic programming sequence " + index;
                            case 5 -> "Dynamic programming optimization " + index;
                            default -> "Greedy algorithm proof " + index;
                        };
                        stream.showText("1." + index + " " + detail);
                        stream.newLineAtOffset(0, -14);
                    }
                    stream.endText();
                }
            }
            document.save(output); return output.toByteArray();
        }
    }
}

