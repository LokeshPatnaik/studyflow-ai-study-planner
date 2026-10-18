package edu.studyplanner.api;

import edu.studyplanner.security.CurrentStudent;
import edu.studyplanner.agent.AgentService;
import edu.studyplanner.service.PlannerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.UUID;

import static edu.studyplanner.api.ApiModels.*;

@RestController @RequestMapping("/api/v1")
public class PlannerController {
    private final CurrentStudent current; private final PlannerService planner; private final AgentService agent;
    public PlannerController(CurrentStudent current, PlannerService planner, AgentService agent) { this.current = current; this.planner = planner; this.agent = agent; }
    @GetMapping("/subjects") public List<SubjectDto> subjects() { return planner.listSubjects(current.get().id); }
    @PostMapping("/subjects") @ResponseStatus(HttpStatus.CREATED) public SubjectDto addSubject(@Valid @RequestBody SubjectRequest request) { return planner.createSubject(current.get().id, request); }
    @DeleteMapping("/subjects/{subjectId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSubject(@PathVariable UUID subjectId) { planner.deleteSubject(current.get().id, subjectId); }
    @GetMapping("/subjects/{subjectId}/topics") public List<TopicDto> topics(@PathVariable UUID subjectId) { return planner.listTopics(current.get().id, subjectId); }
    @PatchMapping("/subjects/{subjectId}/topics/{topicId}") public TopicDto updateTopic(@PathVariable UUID subjectId, @PathVariable UUID topicId,
            @Valid @RequestBody TopicRequest request) {
        if (!subjectId.equals(request.subjectId())) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Subject does not match the topic request");
        return planner.updateTopic(current.get().id, topicId, request);
    }
    @DeleteMapping("/subjects/{subjectId}/topics/{topicId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTopic(@PathVariable UUID subjectId, @PathVariable UUID topicId) {
        planner.deleteTopic(current.get().id, subjectId, topicId);
    }
    @GetMapping("/exams") public List<ExamDto> exams() { return planner.listExams(current.get().id); }
    @PostMapping("/exams") @ResponseStatus(HttpStatus.CREATED) public ExamDto addExam(@Valid @RequestBody ExamRequest request) { return planner.createExam(current.get().id, request); }
    @PostMapping("/syllabus/upload") public SyllabusUploadDto upload(@RequestPart("file") MultipartFile file) { return planner.parsePdf(file); }
    @GetMapping("/syllabus") public List<SyllabusDto> syllabi() { return planner.listSyllabi(current.get().id); }
    @PostMapping("/syllabus/manual") @ResponseStatus(HttpStatus.CREATED) public List<TopicDto> addTopics(@Valid @RequestBody AddTopicsRequest request) { return planner.addTopics(current.get().id, request); }
    @PostMapping("/plans/generate") @ResponseStatus(HttpStatus.CREATED) public PlanDto generate(@Valid @RequestBody(required = false) PlanRequest request) {
        return planner.generatePlan(current.get().id, request == null ? null : request.title(), "RULE_BASED",
                request == null ? null : request.subjectIds(), request == null ? null : request.unavailableDates(),
                request == null ? null : request.commitments(), request == null ? null : request.timeZone());
    }
    @GetMapping("/plans") public List<PlanDto> plans() { return planner.listPlans(current.get().id); }
    @DeleteMapping("/plans/{planId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePlan(@PathVariable UUID planId) { planner.deletePlan(current.get().id, planId); }
    @GetMapping("/plans/active") public ResponseEntity<PlanDto> active() {
        PlanDto plan = planner.activePlan(current.get().id);
        return plan == null ? ResponseEntity.<PlanDto>noContent().build() : ResponseEntity.ok(plan);
    }
    @PostMapping("/plans/{planId}/approve") public PlanDto approvePlan(@PathVariable UUID planId) { return planner.approvePlan(current.get().id, planId); }
    @PatchMapping("/plans/{planId}/status") public PlanDto planStatus(@PathVariable UUID planId, @Valid @RequestBody PlanStatusRequest request) {
        return planner.updatePlanStatus(current.get().id, planId, request.status());
    }
    @GetMapping("/tasks") public List<TaskDto> tasks(@RequestParam(required = false) String status,
            @RequestParam(required = false) UUID subjectId, @RequestParam(required = false) String search) {
        return planner.listTasks(current.get().id, status, subjectId, search);
    }
    @PatchMapping("/tasks/{taskId}/status") public TaskDto taskStatus(@PathVariable UUID taskId, @Valid @RequestBody TaskStatusRequest request) {
        return planner.updateTaskStatus(current.get().id, taskId, request.status());
    }
    @PatchMapping("/tasks/{taskId}/schedule") public TaskDto taskSchedule(@PathVariable UUID taskId, @Valid @RequestBody TaskScheduleRequest request) {
        return planner.rescheduleTask(current.get().id, taskId, request);
    }
    @GetMapping("/analytics") public ProgressDto analytics() { return planner.progress(current.get().id); }
    @GetMapping("/revisions") public List<RevisionDto> revisions() { return planner.listRevisions(current.get().id); }
    @PostMapping("/revisions/propose") @ResponseStatus(HttpStatus.CREATED) public RevisionDto propose(@Valid @RequestBody(required = false) RevisionRequest request) {
        return planner.proposeRevision(current.get().id, request == null ? null : request.reason());
    }
    @PostMapping("/revisions/{revisionId}/decision") public RevisionDto decide(@PathVariable UUID revisionId, @Valid @RequestBody DecisionRequest request) {
        UUID studentId = current.get().id;
        RevisionDto decision = planner.decideRevision(studentId, revisionId, request.decision().toUpperCase());
        if ("APPROVE".equalsIgnoreCase(request.decision())) agent.applyApprovedRevision(studentId, revisionId);
        return planner.listRevisions(studentId).stream().filter(r -> r.id().equals(revisionId)).findFirst().orElse(decision);
    }
}

