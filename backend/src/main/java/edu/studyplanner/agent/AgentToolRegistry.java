package edu.studyplanner.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.studyplanner.api.ApiModels.*;
import edu.studyplanner.domain.AgentRun;
import edu.studyplanner.domain.ScheduleRevision;
import edu.studyplanner.domain.ToolExecution;
import edu.studyplanner.repository.AgentRunRepository;
import edu.studyplanner.repository.RevisionRepository;
import edu.studyplanner.repository.ToolExecutionRepository;
import edu.studyplanner.service.PlannerService;
import edu.studyplanner.service.PreferenceService;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import java.util.*;
import java.util.function.BiFunction;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Component
public class AgentToolRegistry {
    private final ObjectMapper mapper; private final PlannerService planner; private final PreferenceService preferenceService;
    private final AgentRunRepository runs; private final ToolExecutionRepository executions; private final RevisionRepository revisions;
    private final Map<String, ToolSpec<?>> tools = new LinkedHashMap<>();
    private final ThreadLocal<String> currentMode = ThreadLocal.withInitial(() -> "RULE_BASED");

    public AgentToolRegistry(ObjectMapper mapper, PlannerService planner, PreferenceService preferenceService,
                             AgentRunRepository runs, ToolExecutionRepository executions, RevisionRepository revisions) {
        this.mapper = mapper; this.planner = planner; this.preferenceService = preferenceService; this.runs = runs;
        this.executions = executions; this.revisions = revisions; registerTools();
    }
    public List<ToolSpec<?>> specs() { return List.copyOf(tools.values()); }
    public List<ToolDto> history(UUID studentId) {
        return executions.findTop50ByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .map(e -> new ToolDto(e.toolName, e.status, e.inputSummary, e.outputSummary, e.errorMessage, e.createdAt.toString())).toList();
    }
    public List<ToolDto> history(UUID studentId, UUID runId) {
        return executions.findAllByRunIdOrderByCreatedAtAsc(runId).stream().filter(e -> e.studentId.equals(studentId))
                .map(e -> new ToolDto(e.toolName, e.status, e.inputSummary, e.outputSummary, e.errorMessage, e.createdAt.toString())).toList();
    }
    public List<AgentDto> runs(UUID studentId) {
        return runs.findTop30ByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .map(r -> new AgentDto(r.id, r.stage, r.status, r.mode, r.summary, r.createdAt.toString(),
                        Arrays.asList(r.stageHistory.split(",")), history(studentId, r.id))).toList();
    }
    public ToolResult execute(UUID studentId, UUID runId, String name, String rawArguments) {
        ToolSpec<?> spec = tools.get(name);
        if (spec == null) {
            log(runId, studentId, name, "DENIED", "Arguments rejected", null, "Tool is not registered");
            return new ToolResult(false, null, "Tool is not registered");
        }
        try {
            JsonNode json = mapper.readTree(rawArguments == null || rawArguments.isBlank() ? "{}" : rawArguments);
            if (json == null || !json.isObject()) throw new IllegalArgumentException("Tool arguments must be a JSON object");
            validateShape(spec, json);
            currentMode.set(runId == null ? "RULE_BASED" : runs.findById(runId).map(r -> r.mode).orElse("RULE_BASED"));
            Object value = spec.invoke(studentId, json, mapper);
            String display = mapper.writeValueAsString(value);
            log(runId, studentId, name, "SUCCEEDED", "Arguments validated", truncate(display, 1300), null);
            return new ToolResult(true, value, null);
        } catch (Exception e) {
            String reason = safeMessage(e);
            log(runId, studentId, name, "FAILED", "Arguments validated", null, truncate(reason, 760));
            return new ToolResult(false, null, reason);
        } finally {
            currentMode.remove();
        }
    }
    private void validateShape(ToolSpec<?> spec, JsonNode input) {
        Set<String> allowed = new HashSet<>(); spec.parameters.path("properties").fieldNames().forEachRemaining(allowed::add);
        input.fieldNames().forEachRemaining(name -> { if (!allowed.contains(name)) throw new IllegalArgumentException("Unknown argument: " + name); });
        JsonNode required = spec.parameters.path("required");
        if (required.isArray()) for (JsonNode field : required) {
            JsonNode value = input.get(field.asText()); if (value == null || value.isNull()) throw new IllegalArgumentException("Missing required argument: " + field.asText());
        }
    }
    private void log(UUID run, UUID student, String name, String status, String input, String output, String error) {
        if (run != null && runs.existsById(run)) executions.save(new ToolExecution(run, student, name, status, input, output, error));
    }
    private String safeMessage(Exception e) {
        if (e instanceof ResponseStatusException r) return Objects.toString(r.getReason(), "Request could not be completed");
        if (e instanceof IllegalArgumentException) return Objects.toString(e.getMessage(), "Invalid tool arguments");
        return "Tool could not complete this action";
    }
    private String truncate(String value, int max) { return value.length() <= max ? value : value.substring(0, max - 1) + "…"; }
    private void registerTools() {
        add("parse_syllabus", "Extract likely topic names and units from selectable syllabus text. This does not save topics.", ParseInput.class, schema(Map.of("text", prop("string", "Syllabus text, up to 30,000 characters")), "text"),
                (student, in) -> { if (in.text() == null || in.text().length() > 30000) throw new IllegalArgumentException("Syllabus text is required and must be 30,000 characters or fewer"); return planner.parseSyllabusText(in.text()); });
        add("get_student_preferences", "Retrieve the authenticated student's current study preferences.", EmptyInput.class, schema(Map.of()),
                (student, in) -> preferenceService.toDto(planner.preference(student)));
        add("get_available_time", "Calculate the authenticated student's preferred study window and weekly capacity.", EmptyInput.class, schema(Map.of()),
                (student, in) -> { var p = planner.preference(student); return Map.of("dailyStudyMinutes", p.dailyStudyMinutes, "sessionMinutes", p.sessionMinutes,
                        "breakMinutes", p.breakMinutes, "preferredDays", Arrays.asList(p.preferredDays.split(",")), "dayStart", p.dayStart, "dayEnd", p.dayEnd,
                        "weeklyMinutes", p.dailyStudyMinutes * Arrays.stream(p.preferredDays.split(",")).distinct().count()); });
        add("get_exam_deadlines", "Retrieve exam dates for the authenticated student's subjects.", EmptyInput.class, schema(Map.of()),
                (student, in) -> planner.deadlineDtos(student));
        add("estimate_topic_duration", "Estimate one topic's study duration from difficulty and subject priority. The backend clamps this recommendation.", EstimateInput.class,
                schema(Map.of("difficulty", prop("string", "EASY, MEDIUM, or HARD"), "subjectPriority", prop("integer", "Priority from 1 to 5")), "difficulty", "subjectPriority"),
                (student, in) -> { if (in.subjectPriority() < 1 || in.subjectPriority() > 5) throw new IllegalArgumentException("Subject priority must be 1 through 5");
                    return Map.of("estimatedMinutes", planner.estimateTopicDuration(in.difficulty(), in.subjectPriority())); });
        add("generate_study_plan", "Generate and validate a draft candidate timetable. Draft tasks remain inactive until the student approves them.", GenerateInput.class,
                schema(Map.of("title", prop("string", "Optional plan title"))),
                (student, in) -> { if (in.title() != null && in.title().length() > 120) throw new IllegalArgumentException("Plan title must be 120 characters or fewer");
                    return planner.generatePlan(student, in.title(), currentMode.get()); });
        add("validate_study_plan", "Recheck a saved candidate plan for schedule overlaps and exam deadline violations using deterministic Java rules.", PlanInput.class,
                schema(Map.of("planId", prop("string", "Plan UUID")), "planId"),
                (student, in) -> planner.validatePlan(student, uuid(in.planId())));
        add("create_study_tasks", "Confirm that candidate tasks are stored as a draft for student review. This tool never activates tasks without explicit approval.", PlanInput.class,
                schema(Map.of("planId", prop("string", "Plan UUID")), "planId"),
                (student, in) -> planner.createStudyTasks(student, uuid(in.planId())));
        add("get_progress", "Retrieve completed, pending, and missed study-task progress for the authenticated student.", EmptyInput.class, schema(Map.of()),
                (student, in) -> planner.progress(student));
        add("propose_reschedule", "Create an inactive revision proposal for unfinished tasks. Student approval is required before any schedule changes are applied.", RevisionInput.class,
                schema(Map.of("reason", prop("string", "Brief reason for the proposed adaptation"))),
                (student, in) -> { if (in.reason() != null && in.reason().length() > 600) throw new IllegalArgumentException("Revision reason must be 600 characters or fewer");
                    return planner.proposeRevision(student, in.reason()); });
        add("apply_approved_reschedule", "Apply a schedule revision only if the authenticated student has already approved that exact proposal.", RevisionIdInput.class,
                schema(Map.of("revisionId", prop("string", "Revision UUID explicitly approved by the student")), "revisionId"),
                (student, in) -> { ScheduleRevision r = planner.requireRevision(student, uuid(in.revisionId())); planner.applyApprovedRevision(student, r);
                    return Map.of("revisionId", r.id, "status", r.status); });
    }
    private ObjectNode prop(String type, String description) { ObjectNode n = mapper.createObjectNode(); n.put("type", type); n.put("description", description); return n; }
    private ObjectNode schema(Map<String, ObjectNode> properties, String... required) {
        ObjectNode n = mapper.createObjectNode(); n.put("type", "object"); ObjectNode p = n.putObject("properties"); properties.forEach(p::set);
        if (required != null && required.length > 0) { var a = n.putArray("required"); Arrays.stream(required).forEach(a::add); }
        n.put("additionalProperties", false); return n;
    }
    private UUID uuid(String id) { try { return UUID.fromString(id); } catch (Exception e) { throw new IllegalArgumentException("Expected a valid UUID"); } }
    private <I> void add(String name, String description, Class<I> inputType, JsonNode schema, BiFunction<UUID, I, Object> action) {
        tools.put(name, new ToolSpec<>(name, description, inputType, schema, action));
    }

    public record ToolResult(boolean success, Object data, String error) {}
    public record EmptyInput() {}
    public record ParseInput(String text) {}
    public record EstimateInput(String difficulty, int subjectPriority) {}
    public record GenerateInput(String title) {}
    public record PlanInput(String planId) {}
    public record RevisionInput(String reason) {}
    public record RevisionIdInput(String revisionId) {}
    public static final class ToolSpec<I> {
        public final String name, description; public final Class<I> inputType; public final JsonNode parameters;
        private final BiFunction<UUID, I, Object> action;
        ToolSpec(String name, String description, Class<I> inputType, JsonNode parameters, BiFunction<UUID, I, Object> action) {
            this.name=name; this.description=description; this.inputType=inputType; this.parameters=parameters; this.action=action;
        }
        Object invoke(UUID studentId, JsonNode input, ObjectMapper mapper) throws Exception {
            I typed = mapper.treeToValue(input, inputType);
            return action.apply(studentId, typed);
        }
    }
}

