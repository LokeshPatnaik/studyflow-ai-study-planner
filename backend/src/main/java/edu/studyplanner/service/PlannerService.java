package edu.studyplanner.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.studyplanner.api.ApiModels.*;
import edu.studyplanner.agent.SyllabusTopicParser;
import edu.studyplanner.agent.SyllabusTopicGrouper;
import edu.studyplanner.domain.*;
import edu.studyplanner.repository.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class PlannerService {
    private final SubjectRepository subjects; private final TopicRepository topics; private final ExaminationRepository exams;
    private final StudyPlanRepository plans; private final StudyTaskRepository tasks; private final RevisionRepository revisions;
    private final PreferenceRepository preferences; private final SyllabusRepository syllabi; private final ObjectMapper mapper; private final SyllabusTopicParser openai;
    private final SyllabusTopicGrouper topicGrouper = new SyllabusTopicGrouper();

    public PlannerService(SubjectRepository subjects, TopicRepository topics, ExaminationRepository exams,
                          StudyPlanRepository plans, StudyTaskRepository tasks, RevisionRepository revisions,
                          PreferenceRepository preferences, SyllabusRepository syllabi, ObjectMapper mapper, SyllabusTopicParser openai) {
        this.subjects = subjects; this.topics = topics; this.exams = exams; this.plans = plans; this.tasks = tasks;
        this.revisions = revisions; this.preferences = preferences; this.syllabi = syllabi; this.mapper = mapper; this.openai = openai;
    }

    @Transactional(readOnly = true) public List<SubjectDto> listSubjects(UUID studentId) {
        return subjects.findAllByStudentIdOrderByNameAsc(studentId).stream().map(s -> {
            Examination exam = exams.findFirstByStudentIdAndSubjectIdOrderByExamDateAsc(studentId, s.id).orElse(null);
            List<SyllabusTopic> subjectTopics = topics.findAllBySubjectIdOrderByTitleAsc(s.id);
            Set<UUID> completeTopicIds = tasks.findAllByStudentIdOrderByScheduledDateAscStartTimeAsc(studentId).stream()
                    .filter(t -> "COMPLETED".equals(t.status) && t.topicId != null).map(t -> t.topicId).collect(Collectors.toSet());
            return new SubjectDto(s.id, s.name, s.color, s.priority, exam == null ? "Examination" : exam.name,
                    exam == null ? null : exam.examDate, subjectTopics.size(), subjectTopics.stream().filter(t -> completeTopicIds.contains(t.id)).count());
        }).toList();
    }
    @Transactional public SubjectDto createSubject(UUID studentId, SubjectRequest request) {
        Subject s = subjects.save(new Subject(studentId, request.name().trim(), request.color() == null ? "#8b7cff" : request.color(), request.priority()));
        exams.save(new Examination(studentId, s.id, request.examName().trim(), request.examDate()));
        return listSubjects(studentId).stream().filter(x -> x.id().equals(s.id)).findFirst().orElseThrow();
    }
    @Transactional public ExamDto createExam(UUID studentId, ExamRequest request) {
        requireSubject(studentId, request.subjectId());
        Examination e = exams.save(new Examination(studentId, request.subjectId(), request.name().trim(), request.examDate()));
        return new ExamDto(e.id, e.subjectId, e.name, e.examDate);
    }
    @Transactional(readOnly = true) public List<ExamDto> listExams(UUID studentId) {
        return exams.findAllByStudentIdOrderByExamDateAsc(studentId).stream().map(e -> new ExamDto(e.id, e.subjectId, e.name, e.examDate)).toList();
    }
    @Transactional public List<TopicDto> addTopics(UUID studentId, AddTopicsRequest request) {
        requireSubject(studentId, request.subjectId());
        List<SyllabusTopic> saved = request.topics().stream().map(t -> {
            SyllabusTopic topic = new SyllabusTopic(request.subjectId(), t.title().trim(), t.unit(),
                    normalizeDifficulty(t.difficulty()), newTopicEstimateMinutes(), true);
            topic.chapter = t.chapter(); topic.description = t.description();
            topic.importance = t.importance() == null ? 3 : clamp(t.importance(), 1, 5);
            topic.familiarity = t.familiarity() == null ? 3 : clamp(t.familiarity(), 1, 5);
            topic.included = t.included() == null || t.included();
            topic.sourceContent = serializeSourceContent(t.sourceContent());
            return topics.save(topic);
        }).toList();
        String source = request.source() == null ? "MANUAL" : request.source().toUpperCase(Locale.ROOT);
        String sourceName = "PDF".equals(source) && request.sourceName() != null && !request.sourceName().isBlank()
                ? request.sourceName().trim() : ("PDF".equals(source) ? "Imported PDF syllabus" : "Manual topic entry");
        syllabi.save(new Syllabus(studentId, request.subjectId(), sourceName, source, saved.size()));
        return saved.stream().map(this::topicDto).toList();
    }
    @Transactional(readOnly = true) public List<SyllabusDto> listSyllabi(UUID studentId) {
        Map<UUID, Subject> subjectById = subjects.findAllByStudentIdOrderByNameAsc(studentId).stream().collect(Collectors.toMap(s -> s.id, s -> s));
        return syllabi.findAllByStudentIdOrderByCreatedAtDesc(studentId).stream().map(s -> new SyllabusDto(s.id, s.subjectId,
                subjectById.containsKey(s.subjectId) ? subjectById.get(s.subjectId).name : "Subject", s.sourceName, s.sourceType,
                s.topicCount, s.status, s.createdAt.toString())).toList();
    }
    @Transactional(readOnly = true) public List<TopicDto> listTopics(UUID studentId, UUID subjectId) {
        requireSubject(studentId, subjectId); return topics.findAllBySubjectIdOrderByTitleAsc(subjectId).stream().map(this::topicDto).toList();
    }
    @Transactional public void deleteTopic(UUID studentId, UUID subjectId, UUID topicId) {
        requireSubject(studentId, subjectId);
        SyllabusTopic topic = topics.findByIdAndSubjectId(topicId, subjectId).orElseThrow(() -> notFound("Topic not found"));
        topics.delete(topic);
    }
    @Transactional public void deleteSubject(UUID studentId, UUID subjectId) {
        Subject subject = subjects.findByIdAndStudentId(subjectId, studentId).orElseThrow(() -> notFound("Subject not found"));

        // Keep plans and their other subjects intact, while removing this subject's
        // proposal entries so an old revision cannot recreate deleted sessions.
        for (ScheduleRevision revision : revisions.findAllByStudentIdOrderByCreatedAtDesc(studentId)) {
            List<ProposedTask> proposed = readProposals(revision.proposedJson);
            List<ProposedTask> retained = proposed.stream().filter(task -> !subjectId.equals(task.subjectId())).toList();
            if (retained.size() == proposed.size()) continue;
            revision.proposedJson = writeJson(retained);
            String revisionNote = "Subject “" + subject.name + "” was removed; its proposed sessions were removed. " + revision.reason;
            revision.reason = revisionNote.substring(0, Math.min(1400, revisionNote.length()));
            if (retained.isEmpty() && Set.of("PENDING", "APPROVED").contains(revision.status)) {
                revision.status = "REJECTED";
                revision.decidedAt = Instant.now();
            }
            revisions.save(revision);
        }

        // V1 defines ON DELETE CASCADE for syllabus topics, syllabi, examinations,
        // and study tasks. This is intentional: sessions and spaced revisions are
        // study_tasks, while plans and their revision history are retained.
        subjects.delete(subject);
    }
    @Transactional public void deletePlan(UUID studentId, UUID planId) {
        StudyPlan plan = plans.findByIdAndStudentIdForUpdate(planId, studentId).orElseThrow(() -> notFound("Study plan not found"));
        // V1's plan foreign keys cascade to study tasks and schedule revisions.
        plans.delete(plan);
    }
    @Transactional public SyllabusUploadDto parsePdf(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a PDF syllabus first");
        if (file.getSize() > 10L * 1024 * 1024) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "PDF must be 10 MB or smaller");
        String name = Objects.toString(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
        if (!name.endsWith(".pdf") || file.getContentType() != null && !file.getContentType().equalsIgnoreCase("application/pdf"))
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only PDF files are supported");
        try (var document = Loader.loadPDF(file.getBytes())) {
            String text = new PDFTextStripper().getText(document).strip();
            if (text.isBlank()) return new SyllabusUploadDto(false, true, "This PDF contains no selectable text. It may be scanned; OCR is not enabled in this build.", "", List.of());
            List<String> pageTexts = new ArrayList<>();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                PDFTextStripper pageStripper = new PDFTextStripper();
                pageStripper.setStartPage(page); pageStripper.setEndPage(page);
                pageTexts.add(pageStripper.getText(document));
            }
            String extractedText = String.join("\n", pageTexts).strip();
            List<ParsedTopic> parsed = topicGrouper.groupPages(pageTexts); boolean aiUsed = false; boolean aiUnavailable = false;
            if (openai.configured()) {
                try {
                    List<ParsedTopic> aiSuggestions = openai.parseSyllabusTopics(extractedText.substring(0, Math.min(extractedText.length(), 30000)));
                    if (!aiSuggestions.isEmpty()) { parsed = applyAiGuidance(parsed, aiSuggestions); aiUsed = true; }
                    else aiUnavailable = true;
                }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); aiUnavailable = true; }
                catch (Exception e) { aiUnavailable = true; }
            }
            String message = parsed.isEmpty() ? "Text was extracted, but no topic list was recognized. Add topics manually or edit the extracted text."
                    : aiUsed ? "Related headings were grouped locally. AI provided additional difficulty and time guidance; review and edit each topic before saving."
                    : aiUnavailable ? "AI parsing was unavailable; local rule-based extraction provided these suggestions. Review them before saving."
                    : "Local rule-based extraction provided these suggestions. Add OPENAI_API_KEY for AI-assisted parsing. Review them before saving.";
            return new SyllabusUploadDto(true, false, message, extractedText, parsed);
        } catch (IOException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The PDF could not be read. Check that it is a valid, unencrypted PDF.");
        }
    }
    private List<ParsedTopic> parseTopicLines(String text) {
        return topicGrouper.groupPages(List.of(text));
    }
    public List<ParsedTopic> parseSyllabusText(String text) {
        if (text == null || text.isBlank()) throw bad("Provide syllabus text to parse");
        if (text.length() > 30000) throw bad("Syllabus text must be 30,000 characters or fewer");
        return parseTopicLines(text);
    }

    private List<ParsedTopic> applyAiGuidance(List<ParsedTopic> grouped, List<ParsedTopic> suggestions) {
        if (grouped.isEmpty()) return grouped;
        return grouped.stream().map(topic -> {
            List<ParsedTopic> matches = suggestions.stream().filter(suggestion -> topic.sourceContent().stream()
                    .filter(SourceText::heading).anyMatch(source -> similarHeading(source.text(), suggestion.title()))).toList();
            if (matches.isEmpty()) return topic;
            int aiMinutes = matches.stream().mapToInt(ParsedTopic::estimatedMinutes).sum();
            String difficulty = matches.stream().map(ParsedTopic::difficulty).min(Comparator.comparingInt(this::difficultyRank)).orElse(topic.difficulty());
            int estimated = clamp(Math.max(topic.estimatedMinutes(), aiMinutes), 15, 720);
            return new ParsedTopic(topic.title(), topic.unit(), topic.chapter(), topic.description(), difficulty, estimated,
                    topic.importance(), topic.familiarity(), topic.included(), topic.sourceContent());
        }).toList();
    }
    private boolean similarHeading(String source, String suggested) {
        String clean = source.replaceFirst("^\\s*(?:\\d+(?:\\.\\d+){0,4}|[A-Z]|[IVXLCDM]+)[.)]?\\s+", "").trim();
        return clean.equalsIgnoreCase(suggested) || clean.toLowerCase(Locale.ROOT).contains(suggested.toLowerCase(Locale.ROOT))
                || suggested.toLowerCase(Locale.ROOT).contains(clean.toLowerCase(Locale.ROOT));
    }
    private String serializeSourceContent(List<SourceText> sourceContent) {
        if (sourceContent == null || sourceContent.isEmpty()) return null;
        try { return mapper.writeValueAsString(sourceContent); }
        catch (Exception e) { throw new IllegalStateException("Could not save original syllabus content", e); }
    }
    private List<SourceText> deserializeSourceContent(String value) {
        if (value == null || value.isBlank()) return List.of();
        try { return mapper.readValue(value, new TypeReference<>() {}); }
        catch (Exception e) { return List.of(); }
    }

    @Transactional public PlanDto generatePlan(UUID studentId, String requestedTitle, String mode) {
        return generatePlan(studentId, requestedTitle, mode, null);
    }
    @Transactional public PlanDto generatePlan(UUID studentId, String requestedTitle, String mode, List<UUID> selectedSubjectIds) {
        return generatePlan(studentId, requestedTitle, mode, selectedSubjectIds, List.of(), List.of(), null);
    }
    @Transactional public PlanDto generatePlan(UUID studentId, String requestedTitle, String mode, List<UUID> selectedSubjectIds,
                                               List<LocalDate> unavailableDates, List<CommitmentRequest> commitments, String timeZone) {
        String title = requestedTitle == null || requestedTitle.isBlank() ? "Exam preparation plan" : requestedTitle.trim();
        StudyPlan existingDraft = plans.findFirstByStudentIdAndStatusOrderByCreatedAtDesc(studentId, "DRAFT").orElse(null);
        if (existingDraft != null && existingDraft.title.equals(title))
            return planDto(existingDraft, tasks.findAllByStudentIdAndPlanIdOrderByScheduledDateAscStartTimeAsc(studentId, existingDraft.id), true, List.of());
        List<Subject> studentSubjects = subjects.findAllByStudentIdOrderByNameAsc(studentId);
        if (selectedSubjectIds != null && !selectedSubjectIds.isEmpty()) {
            Set<UUID> selected = new HashSet<>(selectedSubjectIds);
            studentSubjects = studentSubjects.stream().filter(s -> selected.contains(s.id)).toList();
            if (studentSubjects.size() != selected.size()) throw notFound("One or more selected subjects were not found");
        }
        if (studentSubjects.isEmpty()) throw bad("Add at least one subject before generating a study plan");
        List<UUID> subjectIds = studentSubjects.stream().map(s -> s.id).toList();
        List<SyllabusTopic> allTopics = topics.findAllBySubjectIdInAndApprovedTrueAndIncludedTrue(subjectIds);
        if (allTopics.isEmpty()) throw bad("Add or approve syllabus topics before generating a study plan");
        StudentPreference pref = preferences.findById(studentId).orElseGet(() -> new StudentPreference(studentId));
        Map<UUID, Subject> subjectById = studentSubjects.stream().collect(Collectors.toMap(s -> s.id, s -> s));
        ZoneId zone = planningZone(timeZone);
        LocalDate today = LocalDate.now(zone);
        LocalTime now = LocalTime.now(zone);
        Map<UUID, LocalDate> deadlines = new HashMap<>();
        for (Subject s : studentSubjects) exams.findFirstByStudentIdAndSubjectIdOrderByExamDateAsc(studentId, s.id).ifPresent(e -> deadlines.put(s.id, e.examDate));
        List<EstimatedTopic> estimates = allTopics.stream().map(topic -> {
            Subject subject = subjectById.get(topic.subjectId);
            LocalDate deadline = deadlines.getOrDefault(subject.id, today.plusDays(14));
            return estimateTopic(subject, topic, deadline);
        }).sorted(Comparator.comparing((EstimatedTopic e) -> e.deadline)
                .thenComparing((EstimatedTopic e) -> -e.topic.importance)
                .thenComparing((EstimatedTopic e) -> -e.subject.priority)
                .thenComparing(e -> difficultyRank(e.topic.difficulty))).toList();
        Set<LocalDate> unavailable = unavailableDates == null ? Set.of() : new HashSet<>(unavailableDates);
        List<CommitmentRequest> blockedTimes = commitments == null ? List.of() : commitments;
        for (CommitmentRequest commitment : blockedTimes)
            if (commitment.endTime() == null || commitment.startTime() == null || !commitment.endTime().isAfter(commitment.startTime()))
                throw bad("Existing commitments must have an end time after their start time");
        List<StudyTask> occupied = tasks.findAllByStudentIdOrderByScheduledDateAscStartTimeAsc(studentId).stream()
                .filter(t -> "PLANNED".equals(t.status) && !t.scheduledDate.isBefore(today)).toList();
        int latestWindow = estimates.stream().mapToInt(e -> Math.max(0, (int)ChronoUnit.DAYS.between(today, e.deadline))).max().orElse(0);
        Capacity capacity = calculateCapacity(today, latestWindow, pref, unavailable, blockedTimes, occupied, now);
        PlanAllocation allocation = allocatePlan(estimates, pref, unavailable, blockedTimes, occupied, today, now, capacity);
        Map<UUID, Integer> scheduledByTopic = allocation.tasks.stream().collect(Collectors.groupingBy(item -> item.topic.id,
                Collectors.summingInt(ScheduledItem::durationMinutes)));
        int totalEstimated = estimates.stream().mapToInt(e -> e.learningMinutes + e.practiceMinutes + e.revisionMinutes).sum();
        int scheduledMinutes = allocation.tasks.stream().mapToInt(ScheduledItem::durationMinutes).sum();
        int shortfall = Math.max(0, totalEstimated - scheduledMinutes);
        List<WorkloadTopicDto> topicWorkload = estimates.stream().map(e -> {
            int topicScheduled = scheduledByTopic.getOrDefault(e.topic.id, 0);
            int total = e.learningMinutes + e.practiceMinutes + e.revisionMinutes;
            return new WorkloadTopicDto(e.topic.id, e.subject.id, e.subject.name, e.topic.title, e.deadline, e.topic.importance,
                    e.learningMinutes, e.practiceMinutes, e.revisionMinutes, total, topicScheduled, Math.max(0, total - topicScheduled));
        }).toList();
        WorkloadDto workload = new WorkloadDto(totalEstimated, capacity.availableMinutes, capacity.bufferMinutes,
                scheduledMinutes, shortfall, topicWorkload);
        List<ScheduledItem> timetable = allocation.tasks.stream().sorted(Comparator.comparing(ScheduledItem::date)
                .thenComparing(ScheduledItem::start)).toList();
        boolean feasible = shortfall == 0 && allocation.conflicts.isEmpty();
        if (!feasible) {
            String summary = "Estimated workload exceeds the usable time before the selected exams by " + shortfall
                    + " minutes. The preview prioritizes nearer exams and more important topics; all topics remain listed below.";
            return new PlanDto(null, title, "UNSAVED", mode, summary, false, allocation.conflicts,
                    timetable.stream().map(this::previewTaskDto).toList(), workload);
        }
        String summary = "A draft timetable using each saved topic estimate as its full initial study block, with "
                + capacity.bufferMinutes + " minutes reserved as a schedule buffer. Spaced revisions are scheduled after learning is completed.";
        StudyPlan plan = plans.save(new StudyPlan(studentId, title, "DRAFT", mode, summary));
        List<StudyTask> saved = timetable.stream().map(item -> {
            StudyTask task = new StudyTask(plan.id, studentId, item.subject.id, item.topic.id, item.title, item.description,
                    item.date, item.start, item.end, item.durationMinutes, item.priority, "PROPOSED", item.deadline);
            task.sessionType = item.sessionType;
            task.revisionNumber = item.revisionNumber;
            return tasks.save(task);
        }).toList();
        return planDto(plan, saved, true, List.of(), workload);
    }
    @Transactional public PlanDto approvePlan(UUID studentId, UUID planId) {
        StudyPlan plan = plans.findByIdAndStudentId(planId, studentId).orElseThrow(() -> notFound("Study plan not found"));
        if (!"DRAFT".equals(plan.status)) throw bad("Only a draft plan can be approved");
        plans.findAllByStudentIdOrderByCreatedAtDesc(studentId).stream().filter(p -> "ACTIVE".equals(p.status)).forEach(old -> {
            old.status = "ARCHIVED"; plans.save(old);
            tasks.findAllByPlanIdOrderByScheduledDateAscStartTimeAsc(old.id).stream().filter(t -> "PLANNED".equals(t.status)).forEach(t -> { t.status = "ARCHIVED"; tasks.save(t); });
        });
        plan.status = "ACTIVE"; plans.save(plan);
        List<StudyTask> planTasks = tasks.findAllByStudentIdAndPlanIdOrderByScheduledDateAscStartTimeAsc(studentId, planId);
        planTasks.forEach(t -> { if ("PROPOSED".equals(t.status)) { t.status = "PLANNED"; tasks.save(t); } });
        return planDto(plan, planTasks, true, List.of());
    }
    @Transactional(readOnly = true) public List<PlanDto> listPlans(UUID studentId) {
        return plans.findAllByStudentIdOrderByCreatedAtDesc(studentId).stream().map(p -> planDto(p,
                tasks.findAllByStudentIdAndPlanIdOrderByScheduledDateAscStartTimeAsc(studentId, p.id), true, List.of())).toList();
    }
    @Transactional(readOnly = true) public PlanDto activePlan(UUID studentId) {
        StudyPlan p = plans.findFirstByStudentIdAndStatusOrderByCreatedAtDesc(studentId, "ACTIVE").orElse(null);
        return p == null ? null : planDto(p, tasks.findAllByStudentIdAndPlanIdOrderByScheduledDateAscStartTimeAsc(studentId, p.id), true, List.of());
    }
    @Transactional(readOnly = true) public Map<String, Object> validatePlan(UUID studentId, UUID planId) {
        StudyPlan plan = plans.findByIdAndStudentId(planId, studentId).orElseThrow(() -> notFound("Study plan not found"));
        List<StudyTask> planTasks = tasks.findAllByStudentIdAndPlanIdOrderByScheduledDateAscStartTimeAsc(studentId, planId);
        List<String> conflicts = new ArrayList<>();
        for (int i = 0; i < planTasks.size(); i++) for (int j = i + 1; j < planTasks.size(); j++) {
            StudyTask a = planTasks.get(i), b = planTasks.get(j);
            if (a.scheduledDate.equals(b.scheduledDate) && a.startTime != null && b.startTime != null && a.startTime.isBefore(b.endTime) && a.endTime.isAfter(b.startTime))
                conflicts.add("Overlapping sessions on " + a.scheduledDate);
        }
        for (StudyTask t : planTasks) if (t.examDate != null && t.scheduledDate.isAfter(t.examDate)) conflicts.add(t.title + " is after its exam date");
        return Map.of("planId", plan.id, "feasible", conflicts.isEmpty(), "conflicts", conflicts.stream().distinct().toList(), "taskCount", planTasks.size());
    }
    @Transactional(readOnly = true) public Map<String, Object> createStudyTasks(UUID studentId, UUID planId) {
        StudyPlan plan = plans.findByIdAndStudentId(planId, studentId).orElseThrow(() -> notFound("Study plan not found"));
        if (!"DRAFT".equals(plan.status)) throw bad("Only draft plan tasks can be prepared for review");
        long count = tasks.findAllByStudentIdAndPlanIdOrderByScheduledDateAscStartTimeAsc(studentId, planId).stream().filter(t -> "PROPOSED".equals(t.status)).count();
        return Map.of("planId", planId, "draftTasks", count, "approvalRequired", true, "message", "Candidate tasks are saved as a draft and become scheduled only after student approval.");
    }
    @Transactional(readOnly = true) public List<TaskDto> listTasks(UUID studentId, String status, UUID subjectId, String search) {
        String normalizedStatus = status == null || status.isBlank() || "ALL".equalsIgnoreCase(status) ? null : status.toUpperCase();
        String q = search == null ? "" : search.toLowerCase(Locale.ROOT).trim();
        return tasks.findAllByStudentIdOrderByScheduledDateAscStartTimeAsc(studentId).stream()
                .filter(t -> normalizedStatus == null || normalizedStatus.equals(t.status))
                .filter(t -> subjectId == null || subjectId.equals(t.subjectId))
                .filter(t -> q.isBlank() || t.title.toLowerCase(Locale.ROOT).contains(q) || Objects.toString(t.description, "").toLowerCase(Locale.ROOT).contains(q))
                .map(this::taskDto).toList();
    }
    @Transactional public TaskDto updateTaskStatus(UUID studentId, UUID taskId, String status) {
        StudyTask t = tasks.findByIdAndStudentId(taskId, studentId).orElseThrow(() -> notFound("Task not found"));
        if (!List.of("PLANNED", "PROPOSED", "MISSED", "COMPLETED").contains(status.toUpperCase())) throw bad("Unsupported task status");
        if ("PROPOSED".equals(t.status)) throw bad("Approve the draft study plan before updating its tasks");
        if ("ARCHIVED".equals(t.status) || "MOVED".equals(t.status)) throw bad("This task is part of schedule history and cannot be changed");
        StudyPlan taskPlan = plans.findByIdAndStudentIdForUpdate(t.planId, studentId).orElseThrow(() -> notFound("Study plan not found"));
        if (!"ACTIVE".equals(taskPlan.status)) throw bad("Only tasks in an active study plan can be updated");
        String requestedStatus = status.toUpperCase();
        if (requestedStatus.equals(t.status)) return taskDto(t);
        t.status = requestedStatus; t.completedAt = "COMPLETED".equals(t.status) ? Instant.now() : null;
        StudyTask saved = tasks.save(t);
        if ("COMPLETED".equals(saved.status) && "LEARN".equals(saved.sessionType) && saved.topicId != null)
            scheduleRevisionsAfterLearning(saved, taskPlan);
        return taskDto(saved);
    }

    @Transactional public TopicDto updateTopic(UUID studentId, UUID topicId, TopicRequest request) {
        requireSubject(studentId, request.subjectId());
        SyllabusTopic topic = topics.findByIdAndSubjectId(topicId, request.subjectId()).orElseThrow(() -> notFound("Topic not found"));
        topic.title = request.title().trim(); topic.unit = request.unit(); topic.chapter = request.chapter(); topic.description = request.description();
        if (request.difficulty() != null) topic.difficulty = normalizeDifficulty(request.difficulty());
        if (request.importance() != null) topic.importance = request.importance();
        if (request.familiarity() != null) topic.familiarity = request.familiarity();
        if (request.included() != null) topic.included = request.included();
        if (request.sourceContent() != null) topic.sourceContent = serializeSourceContent(request.sourceContent());
        return topicDto(topics.save(topic));
    }

    @Transactional public PlanDto updatePlanStatus(UUID studentId, UUID planId, String requestedStatus) {
        StudyPlan plan = plans.findByIdAndStudentId(planId, studentId).orElseThrow(() -> notFound("Study plan not found"));
        String status = requestedStatus.toUpperCase(Locale.ROOT);
        if ("ARCHIVED".equals(plan.status)) throw bad("Archived plans cannot be changed");
        if ("DRAFT".equals(plan.status) && !"ARCHIVED".equals(status)) throw bad("Approve or archive a draft before changing its status");
        if ("PAUSED".equals(status) && !"ACTIVE".equals(plan.status)) throw bad("Only an active plan can be paused");
        if ("ACTIVE".equals(status) && !List.of("PAUSED", "ACTIVE").contains(plan.status)) throw bad("Only a paused plan can be resumed");
        if ("ACTIVE".equals(status)) {
            if ("ARCHIVED".equals(plan.status)) throw bad("Archived plans cannot be resumed");
            boolean anotherActive = plans.findAllByStudentIdOrderByCreatedAtDesc(studentId).stream()
                    .anyMatch(other -> !other.id.equals(plan.id) && "ACTIVE".equals(other.status));
            if (anotherActive) throw new ResponseStatusException(HttpStatus.CONFLICT, "Pause or archive the current active plan before resuming this one");
        }
        plan.status = status; plans.save(plan);
        List<StudyTask> planTasks = tasks.findAllByStudentIdAndPlanIdOrderByScheduledDateAscStartTimeAsc(studentId, planId);
        if ("ARCHIVED".equals(status)) planTasks.stream().filter(t -> "PLANNED".equals(t.status)).forEach(t -> { t.status = "ARCHIVED"; tasks.save(t); });
        return planDto(plan, planTasks, true, List.of());
    }

    @Transactional public TaskDto rescheduleTask(UUID studentId, UUID taskId, TaskScheduleRequest request) {
        StudyTask task = tasks.findByIdAndStudentId(taskId, studentId).orElseThrow(() -> notFound("Task not found"));
        if (!List.of("PLANNED", "MISSED").contains(task.status)) throw bad("Only unfinished sessions can be moved");
        StudyPlan plan = plans.findByIdAndStudentId(task.planId, studentId).orElseThrow(() -> notFound("Study plan not found"));
        if (!"ACTIVE".equals(plan.status)) throw bad("Only sessions in an active plan can be moved");
        int duration = request.durationMinutes() == null ? task.durationMinutes : request.durationMinutes();
        StudentPreference preference = preferences.findById(studentId).orElseGet(() -> new StudentPreference(studentId));
        LocalDate date = request.scheduledDate(); LocalTime start = request.startTime(); LocalTime end = start.plusMinutes(duration);
        if (date.isBefore(LocalDate.now())) throw bad("A study session cannot be moved into the past");
        if (date.equals(LocalDate.now()) && !start.isAfter(LocalTime.now())) throw bad("A study session cannot be moved to a time that has already passed");
        if (task.examDate != null && !date.isBefore(task.examDate)) throw bad("Schedule this session before its exam date");
        String day = date.getDayOfWeek().name().substring(0, 3);
        Set<String> availableDays = Arrays.stream(preference.preferredDays.split(",")).map(String::trim).collect(Collectors.toSet());
        if (!availableDays.contains(day)) throw bad("This date is outside your available study days");
        if (start.isBefore(preference.dayStart) || end.isAfter(preference.dayEnd)) throw bad("This session must fit inside your preferred study window");
        List<StudyTask> otherSessions = tasks.findAllByStudentIdOrderByScheduledDateAscStartTimeAsc(studentId).stream()
                .filter(other -> !other.id.equals(task.id) && date.equals(other.scheduledDate) && "PLANNED".equals(other.status) && other.startTime != null).toList();
        if (otherSessions.stream().anyMatch(other -> start.isBefore(other.endTime) && end.isAfter(other.startTime)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This move overlaps another planned study session");
        boolean violatesBreak = otherSessions.stream().anyMatch(other -> {
            long gap = end.isBefore(other.startTime) || end.equals(other.startTime)
                    ? Duration.between(end, other.startTime).toMinutes()
                    : Duration.between(other.endTime, start).toMinutes();
            return (end.compareTo(other.startTime) <= 0 || start.compareTo(other.endTime) >= 0) && gap < preference.breakMinutes;
        });
        if (violatesBreak) throw new ResponseStatusException(HttpStatus.CONFLICT, "Leave your configured break between planned sessions");
        int used = otherSessions.stream().mapToInt(other -> other.durationMinutes).sum();
        if (used + duration > preference.dailyStudyMinutes) throw new ResponseStatusException(HttpStatus.CONFLICT, "This move exceeds your daily study limit");
        task.scheduledDate = date; task.startTime = start; task.endTime = end; task.durationMinutes = duration;
        if ("MISSED".equals(task.status)) task.status = "PLANNED";
        return taskDto(tasks.save(task));
    }

    private void scheduleRevisionsAfterLearning(StudyTask completed, StudyPlan plan) {
        List<StudyTask> learning = tasks.findAllByPlanIdAndTopicIdAndSessionType(plan.id, completed.topicId, "LEARN");
        if (learning.isEmpty() || learning.stream().anyMatch(t -> !"COMPLETED".equals(t.status))) return;
        if (tasks.findAllByPlanIdAndTopicIdAndSessionType(plan.id, completed.topicId, "REVISION").stream()
                .anyMatch(task -> task.revisionNumber > 0)) return;
        SyllabusTopic topic = topics.findById(completed.topicId).orElse(null);
        if (topic == null || completed.examDate == null) return;
        StudentPreference preference = preferences.findById(completed.studentId).orElseGet(() -> new StudentPreference(completed.studentId));
        int[] offsets = {1, 3, 7};
        for (int index = 0; index < offsets.length; index++) {
            LocalDate target = completed.scheduledDate.plusDays(offsets[index]);
            if (!target.isBefore(completed.examDate)) continue;
            Slot slot = findRevisionSlot(completed, target, preference, 25);
            if (slot == null) continue;
            StudyTask revision = new StudyTask(plan.id, completed.studentId, completed.subjectId, completed.topicId,
                    "Recall · " + topic.title, "Spaced review " + (index + 1) + ": retrieve the key ideas from memory, then check your notes.",
                    slot.date(), slot.start(), slot.end(), 25, completed.priority, "PLANNED", completed.examDate);
            revision.sessionType = "REVISION"; revision.revisionNumber = index + 1;
            tasks.save(revision);
        }
    }

    private Slot findRevisionSlot(StudyTask source, LocalDate target, StudentPreference preference, int duration) {
        Set<String> available = Arrays.stream(preference.preferredDays.split(",")).map(String::trim).collect(Collectors.toSet());
        LocalDate lastDay = source.examDate.minusDays(1);
        for (LocalDate day = target; !day.isAfter(lastDay); day = day.plusDays(1)) {
            if (!available.contains(day.getDayOfWeek().name().substring(0, 3))) continue;
            List<StudyTask> scheduled = new ArrayList<>();
            for (StudyTask task : tasks.findAllByStudentIdOrderByScheduledDateAscStartTimeAsc(source.studentId))
                if (day.equals(task.scheduledDate) && "PLANNED".equals(task.status) && task.startTime != null) scheduled.add(task);
            if (scheduled.stream().mapToInt(t -> t.durationMinutes).sum() + duration > preference.dailyStudyMinutes) continue;
            LocalTime candidate = preference.dayStart;
            for (StudyTask existing : scheduled) {
                if (!candidate.plusMinutes(duration + preference.breakMinutes).isAfter(existing.startTime)) break;
                if (candidate.isBefore(existing.endTime.plusMinutes(preference.breakMinutes))) candidate = existing.endTime.plusMinutes(preference.breakMinutes);
            }
            LocalTime end = candidate.plusMinutes(duration);
            boolean overlaps = false;
            for (StudyTask existing : scheduled) if (candidate.isBefore(existing.endTime) && end.isAfter(existing.startTime)) overlaps = true;
            if (!end.isAfter(preference.dayEnd) && !overlaps) return new Slot(day, candidate, end);
        }
        return null;
    }
    private record Slot(LocalDate date, LocalTime start, LocalTime end) {}
    @Transactional(readOnly = true) public ProgressDto progress(UUID studentId) {
        List<StudyTask> all = tasks.findAllByStudentIdOrderByScheduledDateAscStartTimeAsc(studentId).stream()
                .filter(t -> !List.of("ARCHIVED", "MOVED", "PROPOSED").contains(t.status)).toList();
        long completed = all.stream().filter(t -> "COMPLETED".equals(t.status)).count();
        long missed = all.stream().filter(t -> "MISSED".equals(t.status)).count();
        long pending = all.stream().filter(t -> "PLANNED".equals(t.status)).count();
        int percent = all.isEmpty() ? 0 : (int)Math.round(completed * 100.0 / all.size());
        Map<UUID, Subject> byId = subjects.findAllByStudentIdOrderByNameAsc(studentId).stream().collect(Collectors.toMap(s -> s.id, s -> s));
        List<SubjectProgress> subjectProgress = byId.values().stream().map(s -> {
            List<StudyTask> st = all.stream().filter(t -> t.subjectId.equals(s.id)).toList(); long c = st.stream().filter(t -> "COMPLETED".equals(t.status)).count();
            return new SubjectProgress(s.id, s.name, s.color, st.size(), c, st.isEmpty() ? 0 : (int)Math.round(c * 100.0 / st.size()));
        }).toList();
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<DayProgress> week = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate day = monday.plusDays(i); List<StudyTask> dt = all.stream().filter(t -> t.scheduledDate.equals(day)).toList();
            week.add(new DayProgress(day.getDayOfWeek().name().substring(0, 3), dt.stream().mapToInt(t -> t.durationMinutes).sum(),
                    dt.stream().filter(t -> "COMPLETED".equals(t.status)).mapToInt(t -> t.durationMinutes).sum()));
        }
        return new ProgressDto(all.size(), completed, pending, missed, percent, subjectProgress, week);
    }

    @Transactional public RevisionDto proposeRevision(UUID studentId, String reason) {
        StudyPlan active = plans.findFirstByStudentIdAndStatusOrderByCreatedAtDesc(studentId, "ACTIVE").orElseThrow(() -> bad("Approve a study plan before requesting a revision"));
        ScheduleRevision existingProposal = revisions.findAllByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .filter(r -> r.planId.equals(active.id) && "PENDING".equals(r.status)).findFirst().orElse(null);
        if (existingProposal != null) return revisionDto(existingProposal, tasks.findAllByStudentIdAndPlanIdOrderByScheduledDateAscStartTimeAsc(studentId, active.id));
        List<StudyTask> current = tasks.findAllByStudentIdAndPlanIdOrderByScheduledDateAscStartTimeAsc(studentId, active.id);
        List<StudyTask> moving = current.stream().filter(t -> List.of("PLANNED", "MISSED").contains(t.status)).toList();
        if (moving.isEmpty()) throw bad("There are no unfinished tasks to reschedule");
        StudentPreference pref = preferences.findById(studentId).orElseGet(() -> new StudentPreference(studentId));
        Map<UUID, Subject> byId = subjects.findAllByStudentIdOrderByNameAsc(studentId).stream().collect(Collectors.toMap(s -> s.id, s -> s));
        List<WorkItem> work = moving.stream().map(t -> {
            Subject s = byId.get(t.subjectId); LocalDate deadline = t.examDate == null ? LocalDate.now().plusDays(14) : t.examDate;
            SyllabusTopic topic = t.topicId == null ? new SyllabusTopic(t.subjectId, t.title, null, "MEDIUM", t.durationMinutes, true) : topics.findById(t.topicId).orElse(null);
            if (topic == null) topic = new SyllabusTopic(t.subjectId, t.title, null, "MEDIUM", t.durationMinutes, true);
            return new WorkItem(s, topic, deadline, t.durationMinutes, t.id, t.title, t.description, t.priority);
        }).filter(w -> w.subject != null).sorted(Comparator.comparing(WorkItem::deadline).thenComparing(w -> -w.subject.priority)).toList();
        Set<UUID> movingIds = moving.stream().map(t -> t.id).collect(Collectors.toSet());
        List<StudyTask> occupied = current.stream().filter(t -> "PLANNED".equals(t.status) && !movingIds.contains(t.id)).toList();
        Allocation allocation = allocate(work, pref, occupied, LocalDate.now().plusDays(1));
        if (!allocation.conflicts.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "There is not enough remaining study time before the deadlines: " + String.join("; ", allocation.conflicts));
        String summary = reason == null || reason.isBlank() ? "The plan was adjusted around unfinished tasks, exam deadlines, and your available hours." : reason.trim();
        ScheduleRevision revision = revisions.save(new ScheduleRevision(studentId, active.id, summary, writeJson(allocation.tasks)));
        return revisionDto(revision, current);
    }
    @Transactional public RevisionDto decideRevision(UUID studentId, UUID revisionId, String decision) {
        ScheduleRevision r = revisions.findByIdAndStudentId(revisionId, studentId).orElseThrow(() -> notFound("Revision proposal not found"));
        if (!"PENDING".equals(r.status)) throw bad("This revision has already been decided");
        List<TaskDto> original = tasks.findAllByStudentIdAndPlanIdOrderByScheduledDateAscStartTimeAsc(studentId, r.planId).stream().map(this::taskDto).toList();
        if ("APPROVE".equalsIgnoreCase(decision)) {
            r.status = "APPROVED"; r.decidedAt = Instant.now(); revisions.save(r);
        } else { r.status = "REJECTED"; r.decidedAt = Instant.now(); revisions.save(r); }
        return revisionDto(r, original.stream().map(dto -> tasks.findById(dto.id()).orElse(null)).filter(Objects::nonNull).toList());
    }
    @Transactional(readOnly = true) public List<RevisionDto> listRevisions(UUID studentId) {
        return revisions.findAllByStudentIdOrderByCreatedAtDesc(studentId).stream().map(r -> revisionDto(r,
                tasks.findAllByStudentIdAndPlanIdOrderByScheduledDateAscStartTimeAsc(studentId, r.planId))).toList();
    }
    @Transactional public void applyApprovedRevision(UUID studentId, ScheduleRevision revision) {
        if (!revision.studentId.equals(studentId) || !"APPROVED".equals(revision.status)) throw bad("Student approval is required before applying a revision");
        List<ProposedTask> proposed = readProposals(revision.proposedJson);
        for (ProposedTask p : proposed) {
            if (p.taskId() == null) continue;
            StudyTask old = tasks.findByIdAndStudentId(p.taskId(), studentId).orElseThrow(() -> notFound("A task in this proposal no longer exists"));
            if (!List.of("PLANNED", "MISSED").contains(old.status)) continue;
            old.status = "MOVED"; tasks.save(old);
            tasks.save(new StudyTask(old.planId, studentId, p.subjectId(), p.topicId(), p.title(), p.description(), p.scheduledDate(),
                    p.startTime(), p.endTime(), p.durationMinutes(), p.priority(), "PLANNED", p.examDate()));
        }
        revision.status = "APPLIED"; revision.decidedAt = Instant.now(); revisions.save(revision);
    }
    @Transactional(readOnly = true) public ScheduleRevision requireRevision(UUID studentId, UUID revisionId) {
        return revisions.findByIdAndStudentId(revisionId, studentId).orElseThrow(() -> notFound("Revision proposal not found"));
    }

    public int estimateTopicDuration(String difficulty, int priority) { return newTopicEstimateMinutes(); }
    private EstimatedTopic estimateTopic(Subject subject, SyllabusTopic topic, LocalDate deadline) {
        // The persisted estimate is the complete initial study block. Spaced
        // reviews are scheduled separately after the student completes it.
        return new EstimatedTopic(subject, topic, deadline, topic.estimatedMinutes, 0, 0);
    }
    private int newTopicEstimateMinutes() { return ThreadLocalRandom.current().nextInt(10, 21); }

    private Capacity calculateCapacity(LocalDate today, int daysUntilLatestExam, StudentPreference preference,
                                       Set<LocalDate> unavailableDates, List<CommitmentRequest> commitments,
                                       List<StudyTask> occupied, LocalTime localNow) {
        int windowMinutes = (int)Duration.between(preference.dayStart, preference.dayEnd).toMinutes();
        int total = 0, buffer = 0;
        Map<LocalDate, Integer> dailyLimits = new HashMap<>();
        Map<LocalDate, Integer> dailyBuffers = new HashMap<>();
        for (int offset = 0; offset < daysUntilLatestExam; offset++) {
            LocalDate date = today.plusDays(offset);
            if (unavailableDates.contains(date) || !isAvailableDay(date, preference)) continue;
            LocalTime start = preference.dayStart;
            if (date.equals(today)) {
                LocalTime earliest = localNow.plusMinutes(10).withSecond(0).withNano(0);
                if (start.isBefore(earliest)) start = earliest;
            }
            int window = Math.max(0, (int)Duration.between(start, preference.dayEnd).toMinutes());
            int dailyLimit = Math.min(preference.dailyStudyMinutes, window);
            if (dailyLimit <= 0) continue;
            int occupiedMinutes = busyMinutes(date, occupied, commitments, List.of());
            int dayBuffer = Math.min(Math.max(0, dailyLimit - Math.min(dailyLimit, occupiedMinutes)),
                    dailyLimit >= 60 ? Math.min(15, Math.max(5, roundToFive((int)Math.round(dailyLimit * 0.05)))) : 0);
            total += Math.max(0, dailyLimit - occupiedMinutes);
            buffer += dayBuffer;
            dailyLimits.put(date, dailyLimit);
            dailyBuffers.put(date, dayBuffer);
        }
        return new Capacity(total, buffer, dailyLimits, dailyBuffers);
    }

    private PlanAllocation allocatePlan(List<EstimatedTopic> estimates, StudentPreference preference,
                                        Set<LocalDate> unavailableDates, List<CommitmentRequest> commitments,
                                        List<StudyTask> occupied, LocalDate today, LocalTime localNow, Capacity capacity) {
        List<PlanPhase> phases = estimates.stream().flatMap(estimate -> List.of(
                        new PlanPhase(estimate, "LEARN", estimate.learningMinutes),
                        new PlanPhase(estimate, "PRACTICE", estimate.practiceMinutes),
                        new PlanPhase(estimate, "REVISION", estimate.revisionMinutes)).stream())
                .filter(phase -> phase.minutes > 0)
                .sorted(Comparator.comparing((PlanPhase phase) -> phase.estimate.deadline)
                        .thenComparing((PlanPhase phase) -> -phase.estimate.topic.importance)
                        .thenComparing((PlanPhase phase) -> -phase.estimate.subject.priority)
                        .thenComparingInt(phase -> phaseOrder(phase.sessionType))).toList();
        List<ScheduledItem> placed = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();
        for (PlanPhase phase : phases) {
            int remaining = phase.minutes;
            int part = 1;
            while (remaining >= 10) {
                int duration = nextChunk(remaining, preference.sessionMinutes);
                Slot slot = findPlanSlot(phase.estimate.deadline, duration, preference, unavailableDates, commitments,
                        occupied, placed, capacity, today, localNow);
                if (slot == null) break;
                String label = switch (phase.sessionType) { case "PRACTICE" -> "Practice"; case "REVISION" -> "Revision"; default -> "Learn"; };
                String title = phase.estimate.topic.title + " · " + label + (remaining > duration ? " · part " + part : "");
                String description = switch (phase.sessionType) {
                    case "PRACTICE" -> "Apply " + phase.estimate.topic.title + " with worked problems or questions; record gaps for review.";
                    case "REVISION" -> "Retrieve the key ideas from " + phase.estimate.topic.title + " from memory, then check your notes.";
                    default -> "Learn " + phase.estimate.topic.title + " using active recall and note the important concepts.";
                };
                placed.add(new ScheduledItem(phase.estimate.subject, phase.estimate.topic, title, description,
                        slot.date, slot.start, slot.end, duration,
                        priority(phase.estimate.topic.importance, phase.estimate.topic.difficulty, phase.estimate.deadline, today),
                        phase.estimate.deadline, phase.sessionType, 0));
                remaining -= duration;
                part++;
            }
            if (remaining > 0) conflicts.add(phase.estimate.subject.name + " · " + phase.estimate.topic.title
                    + " has " + remaining + " estimated minutes that do not fit before " + phase.estimate.deadline);
        }
        return new PlanAllocation(placed, conflicts.stream().distinct().toList());
    }

    private Slot findPlanSlot(LocalDate deadline, int duration, StudentPreference preference, Set<LocalDate> unavailableDates,
                              List<CommitmentRequest> commitments, List<StudyTask> occupied, List<ScheduledItem> placed,
                              Capacity capacity, LocalDate today, LocalTime localNow) {
        for (LocalDate date = today; date.isBefore(deadline); date = date.plusDays(1)) {
            Integer dailyLimit = capacity.dailyLimits.get(date);
            if (dailyLimit == null || unavailableDates.contains(date) || !isAvailableDay(date, preference)) continue;
            int reserve = capacity.dailyBuffers.getOrDefault(date, 0);
            int usableLimit = dailyLimit - reserve;
            int busy = busyMinutes(date, occupied, commitments, placed);
            if (busy + duration > usableLimit) continue;
            List<TimeBlock> blocks = busyBlocks(date, occupied, commitments, placed);
            LocalTime start = preference.dayStart;
            if (date.equals(today)) {
                LocalTime earliest = localNow.plusMinutes(10).withSecond(0).withNano(0);
                if (start.isBefore(earliest)) start = earliest;
            }
            blocks.sort(Comparator.comparing(TimeBlock::start));
            for (TimeBlock block : blocks) {
                LocalTime blockStart = block.start.minusMinutes(preference.breakMinutes);
                if (!start.plusMinutes(duration).isAfter(blockStart)) break;
                LocalTime afterBlock = block.end.plusMinutes(preference.breakMinutes);
                if (start.isBefore(afterBlock)) start = afterBlock;
            }
            LocalTime end = start.plusMinutes(duration);
            if (!start.isBefore(preference.dayStart) && !end.isAfter(preference.dayEnd)) return new Slot(date, start, end);
        }
        return null;
    }

    private int busyMinutes(LocalDate date, List<StudyTask> occupied, List<CommitmentRequest> commitments, List<ScheduledItem> placed) {
        int untimed = 0;
        List<TimeBlock> blocks = busyBlocks(date, occupied, commitments, placed);
        for (StudyTask task : occupied) if (task.scheduledDate.equals(date) && task.startTime == null) untimed += task.durationMinutes;
        int union = 0;
        LocalTime start = null, end = null;
        for (TimeBlock block : blocks.stream().sorted(Comparator.comparing(TimeBlock::start)).toList()) {
            if (start == null) { start = block.start; end = block.end; }
            else if (!block.start.isAfter(end)) { if (block.end.isAfter(end)) end = block.end; }
            else { union += (int)Duration.between(start, end).toMinutes(); start = block.start; end = block.end; }
        }
        if (start != null) union += (int)Duration.between(start, end).toMinutes();
        return union + untimed;
    }

    private List<TimeBlock> busyBlocks(LocalDate date, List<StudyTask> occupied, List<CommitmentRequest> commitments, List<ScheduledItem> placed) {
        List<TimeBlock> blocks = new ArrayList<>();
        for (StudyTask task : occupied) if (task.scheduledDate.equals(date) && task.startTime != null && task.endTime != null)
            blocks.add(new TimeBlock(task.startTime, task.endTime));
        for (CommitmentRequest commitment : commitments) if (commitment.date().equals(date))
            blocks.add(new TimeBlock(commitment.startTime(), commitment.endTime()));
        for (ScheduledItem item : placed) if (item.date.equals(date)) blocks.add(new TimeBlock(item.start, item.end));
        return blocks;
    }

    private boolean isAvailableDay(LocalDate date, StudentPreference preference) {
        return Arrays.stream(preference.preferredDays.split(",")).map(String::trim).anyMatch(date.getDayOfWeek().name().substring(0, 3)::equals);
    }
    private int nextChunk(int remaining, int sessionLength) {
        int cap = Math.max(10, sessionLength);
        if (remaining <= cap) return remaining;
        if (remaining - cap < 10) return roundToFive(remaining / 2);
        return cap;
    }
    private int phaseOrder(String type) { return switch (type) { case "LEARN" -> 0; case "PRACTICE" -> 1; default -> 2; }; }
    private int roundToFive(int value) { return Math.max(5, (int)Math.round(value / 5.0) * 5); }
    private ZoneId planningZone(String name) {
        if (name == null || name.isBlank()) return ZoneId.systemDefault();
        try { return ZoneId.of(name); } catch (DateTimeException e) { throw bad("Choose a valid time zone for the schedule"); }
    }
    @Transactional(readOnly = true) public StudentPreference preference(UUID studentId) { return preferences.findById(studentId).orElseGet(() -> new StudentPreference(studentId)); }
    @Transactional(readOnly = true) public List<ExamDto> deadlineDtos(UUID studentId) { return listExams(studentId); }

    private Allocation allocate(List<WorkItem> work, StudentPreference pref, List<StudyTask> occupied, LocalDate firstDay) {
        List<ProposedTask> placed = new ArrayList<>(); List<String> conflicts = new ArrayList<>();
        Map<LocalDate, Integer> usedMinutes = new HashMap<>(); Map<LocalDate, LocalTime> nextTime = new HashMap<>();
        occupied.stream().filter(t -> t.startTime != null).forEach(t -> usedMinutes.merge(t.scheduledDate, t.durationMinutes, Integer::sum));
        Set<String> preferred = Arrays.stream(pref.preferredDays.split(",")).map(String::trim).collect(Collectors.toSet());
        int span = (int)Duration.between(pref.dayStart, pref.dayEnd).toMinutes(); int dailyCap = Math.min(pref.dailyStudyMinutes, span);
        for (WorkItem item : work) {
            int remaining = item.duration; int part = 1; boolean failed = false;
            while (remaining > 0) {
                int sessionCap = Math.max(20, pref.sessionMinutes);
                int chunksLeft = Math.max(1, (int)Math.ceil(remaining / (double)sessionCap));
                int duration = Math.min(sessionCap, (int)Math.ceil(remaining / (double)chunksLeft));
                LocalDate day = nextTime.keySet().stream().filter(d -> !d.isBefore(firstDay)).min(LocalDate::compareTo).orElse(firstDay);
                // Begin each work item at its first day; tasks are sorted by deadline so this preserves urgency.
                boolean found = false;
                while (!day.isAfter(item.deadline)) {
                    String dayCode = day.getDayOfWeek().name().substring(0, 3);
                    int used = usedMinutes.getOrDefault(day, 0);
                    if (preferred.contains(dayCode) && used + duration <= dailyCap) {
                        LocalTime start = nextTime.getOrDefault(day, pref.dayStart);
                        if (start.isBefore(pref.dayStart)) start = pref.dayStart;
                        if (day.equals(LocalDate.now())) {
                            LocalTime now = LocalTime.now().plusMinutes(10).withSecond(0).withNano(0);
                            if (start.isBefore(now)) start = now;
                        }
                        LocalTime end = start.plusMinutes(duration);
                        if (end.isAfter(pref.dayEnd)) { day = day.plusDays(1); continue; }
                        final LocalDate slotDay = day; final LocalTime slotStart = start; final LocalTime slotEnd = end;
                        StudyTask overlap = occupied.stream().filter(t -> t.scheduledDate.equals(slotDay) && t.startTime != null && slotStart.isBefore(t.endTime) && slotEnd.isAfter(t.startTime)).findFirst().orElse(null);
                        if (overlap == null) {
                            String title = item.fixedTitle == null ? (item.topic.title + (item.duration > duration ? " · part " + part : "")) : (item.duration > duration ? item.fixedTitle + " · part " + part : item.fixedTitle);
                            String description = item.fixedDescription == null ? "Study " + item.topic.title + " with active recall, then note questions for review." : item.fixedDescription;
                            placed.add(new ProposedTask(item.oldTaskId, item.subject.id, item.topic.id, item.subject.name, title, description, day, start, end,
                                    duration, item.fixedPriority == null ? priority(Math.max(item.subject.priority, item.topic.importance), item.topic.difficulty, item.deadline) : item.fixedPriority, item.deadline));
                            usedMinutes.put(day, used + duration); nextTime.put(day, end.plusMinutes(pref.breakMinutes)); remaining -= duration; part++; found = true; break;
                        }
                        start = overlap.endTime.plusMinutes(pref.breakMinutes);
                        if (start.plusMinutes(duration).isBefore(pref.dayEnd) || start.plusMinutes(duration).equals(pref.dayEnd)) {
                            nextTime.put(day, start); continue;
                        }
                    }
                    day = day.plusDays(1);
                }
                if (!found) { failed = true; conflicts.add(item.subject.name + " · " + item.topic.title + " cannot fit before " + item.deadline); break; }
            }
            if (failed) continue;
        }
        // Candidate validation is independent from the model and checks all intervals and exam boundaries.
        for (int i = 0; i < placed.size(); i++) for (int j = i + 1; j < placed.size(); j++) {
            ProposedTask a = placed.get(i), b = placed.get(j);
            if (a.scheduledDate().equals(b.scheduledDate()) && a.startTime().isBefore(b.endTime()) && a.endTime().isAfter(b.startTime()))
                conflicts.add("Overlapping sessions on " + a.scheduledDate());
        }
        for (ProposedTask p : placed) if (p.scheduledDate().isAfter(p.examDate())) conflicts.add(p.title() + " falls after its examination deadline");
        return new Allocation(placed, conflicts.stream().distinct().toList());
    }
    private PlanDto planDto(StudyPlan p, List<StudyTask> planTasks, boolean feasible, List<String> conflicts) {
        return planDto(p, planTasks, feasible, conflicts, null);
    }
    private PlanDto planDto(StudyPlan p, List<StudyTask> planTasks, boolean feasible, List<String> conflicts, WorkloadDto workload) {
        return new PlanDto(p.id, p.title, p.status, p.aiMode, p.summary, feasible, conflicts, planTasks.stream().map(this::taskDto).toList(), workload);
    }
    private TaskDto previewTaskDto(ScheduledItem item) {
        return new TaskDto(UUID.randomUUID(), null, item.subject.id, item.topic.id, item.subject.name, item.subject.color,
                item.title, item.description, item.date, item.start, item.end, item.durationMinutes, item.priority, "PROPOSED",
                item.deadline, item.sessionType, item.revisionNumber, null, null, null);
    }
    private TaskDto taskDto(StudyTask t) {
        Subject s = subjects.findById(t.subjectId).orElse(null);
        return new TaskDto(t.id, t.planId, t.subjectId, t.topicId, s == null ? "Subject" : s.name, s == null ? "#8b7cff" : s.color,
                t.title, t.description, t.scheduledDate, t.startTime, t.endTime, t.durationMinutes, t.priority, t.status, t.examDate,
                t.sessionType, t.revisionNumber, t.actualMinutes, t.notes, t.confidence);
    }
    private RevisionDto revisionDto(ScheduleRevision r, List<StudyTask> original) {
        return new RevisionDto(r.id, r.planId, r.status, r.reason, readProposals(r.proposedJson), original.stream().map(this::taskDto).toList(), r.createdAt.toString());
    }
    private String writeJson(List<ProposedTask> data) { try { return mapper.writeValueAsString(data); } catch (Exception e) { throw new IllegalStateException("Could not store revision proposal", e); } }
    private List<ProposedTask> readProposals(String json) { try { return mapper.readValue(json, new TypeReference<>() {}); } catch (Exception e) { throw new IllegalStateException("Stored revision proposal is unreadable", e); } }
    private TopicDto topicDto(SyllabusTopic t) { return new TopicDto(t.id, t.subjectId, t.title, t.unit, t.chapter, t.description,
            t.difficulty, t.estimatedMinutes, t.importance, t.familiarity, t.included, t.approved, t.originalTitle, t.originalUnit, deserializeSourceContent(t.sourceContent)); }
    private Subject requireSubject(UUID studentId, UUID subjectId) { return subjects.findByIdAndStudentId(subjectId, studentId).orElseThrow(() -> notFound("Subject not found")); }
    private String normalizeDifficulty(String d) { return d == null ? "MEDIUM" : switch (d.toUpperCase(Locale.ROOT)) { case "EASY", "HARD" -> d.toUpperCase(Locale.ROOT); default -> "MEDIUM"; }; }
    private int difficultyRank(String d) { return switch (normalizeDifficulty(d)) { case "HARD" -> 0; case "MEDIUM" -> 1; default -> 2; }; }
    private String priority(int p, String difficulty, LocalDate deadline) { return p >= 5 || "HARD".equals(normalizeDifficulty(difficulty)) || ChronoUnit.DAYS.between(LocalDate.now(), deadline) <= 3 ? "HIGH" : p >= 3 ? "MEDIUM" : "LOW"; }
    private String priority(int p, String difficulty, LocalDate deadline, LocalDate today) { return p >= 5 || "HARD".equals(normalizeDifficulty(difficulty)) || ChronoUnit.DAYS.between(today, deadline) <= 3 ? "HIGH" : p >= 3 ? "MEDIUM" : "LOW"; }
    private int clamp(Integer n, int min, int max) { return Math.max(min, Math.min(max, n == null ? 60 : n)); }
    private int clamp(int n, int min, int max) { return Math.max(min, Math.min(max, n)); }
    private ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
    private ResponseStatusException notFound(String m) { return new ResponseStatusException(HttpStatus.NOT_FOUND, m); }
    private record WorkItem(Subject subject, SyllabusTopic topic, LocalDate deadline, int duration, UUID oldTaskId,
                            String fixedTitle, String fixedDescription, String fixedPriority) {
        WorkItem(Subject s, SyllabusTopic t, LocalDate d, int duration) { this(s,t,d,duration,null,null,null,null); }
    }
    private record EstimatedTopic(Subject subject, SyllabusTopic topic, LocalDate deadline,
                                  int learningMinutes, int practiceMinutes, int revisionMinutes) {}
    private record PlanPhase(EstimatedTopic estimate, String sessionType, int minutes) {}
    private record ScheduledItem(Subject subject, SyllabusTopic topic, String title, String description,
                                 LocalDate date, LocalTime start, LocalTime end, int durationMinutes,
                                 String priority, LocalDate deadline, String sessionType, int revisionNumber) {}
    private record PlanAllocation(List<ScheduledItem> tasks, List<String> conflicts) {}
    private record Capacity(int availableMinutes, int bufferMinutes, Map<LocalDate, Integer> dailyLimits,
                            Map<LocalDate, Integer> dailyBuffers) {}
    private record TimeBlock(LocalTime start, LocalTime end) {}
    private record Allocation(List<ProposedTask> tasks, List<String> conflicts) {}
}

