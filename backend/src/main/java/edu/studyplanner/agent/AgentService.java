package edu.studyplanner.agent;

import edu.studyplanner.api.ApiModels.AgentDto;
import edu.studyplanner.domain.AgentRun;
import edu.studyplanner.repository.AgentRunRepository;
import edu.studyplanner.repository.StudyPlanRepository;
import edu.studyplanner.repository.ToolExecutionRepository;
import org.springframework.stereotype.Service;
import java.util.Locale;
import java.util.UUID;

@Service
public class AgentService {
    private final AgentRunRepository runs; private final AgentToolRegistry tools; private final OpenAiFunctionCallingClient openai;
    private final ToolExecutionRepository executions; private final StudyPlanRepository plans;
    public AgentService(AgentRunRepository runs, AgentToolRegistry tools, OpenAiFunctionCallingClient openai,
                        ToolExecutionRepository executions, StudyPlanRepository plans) {
        this.runs=runs; this.tools=tools; this.openai=openai; this.executions=executions; this.plans=plans;
    }
    public AgentDto run(UUID studentId, String intentValue, String note) {
        String intent = intentValue == null ? "REVIEW_PROGRESS" : intentValue.toUpperCase(Locale.ROOT);
        if (!java.util.List.of("GENERATE_PLAN", "ADAPT_PLAN", "REVIEW_PROGRESS").contains(intent)) throw new IllegalArgumentException("Unsupported agent intent");
        boolean ai = openai.configured(); String mode = ai ? "OPENAI_FUNCTIONS" : "RULE_BASED";
        AgentRun run = runs.save(new AgentRun(studentId, "OBSERVE", "RUNNING", mode, "Reading your saved preferences, deadlines, and progress."));
        execute(run, studentId, "get_student_preferences", "{}");
        execute(run, studentId, "get_available_time", "{}");
        execute(run, studentId, "get_exam_deadlines", "{}");
        execute(run, studentId, "get_progress", "{}");
        String summary;
        if (ai) {
            try {
                setStage(run, "ANALYZE"); run.summary = "The AI model is choosing from registered planning tools."; runs.save(run);
                summary = openai.complete(new OpenAiFunctionCallingClient.UUIDContext("account-bound"), intent, note, tools.specs(),
                        (name, args) -> { setStage(run, stageFor(name)); runs.save(run); return execute(run, studentId, name, args); });
                summary = enforcePostconditions(run, studentId, intent, summary);
            } catch (Exception e) {
                boolean actionAlreadyRan = executions.findAllByRunIdOrderByCreatedAtAsc(run.id).stream()
                        .anyMatch(x -> ListActions.contains(x.toolName) && "SUCCEEDED".equals(x.status));
                if (!actionAlreadyRan) { run.mode = "RULE_BASED"; summary = fallback(run, studentId, intent); }
                else { run.mode = "OPENAI_PARTIAL"; summary = "The AI service stopped during a bounded tool run. Any draft or proposal already created is saved for your review; no schedule change is applied automatically."; }
            }
        } else summary = fallback(run, studentId, intent);
        setStage(run, "RECORD"); run.status = "COMPLETED"; run.summary = summary; runs.save(run);
        return new AgentDto(run.id, run.stage, run.status, run.mode, run.summary, run.createdAt.toString(),
                java.util.Arrays.asList(run.stageHistory.split(",")), tools.history(studentId, run.id));
    }
    private AgentToolRegistry.ToolResult execute(AgentRun run, UUID student, String name, String args) {
        return tools.execute(student, run.id, name, args);
    }
    private String fallback(AgentRun run, UUID student, String intent) {
        run.mode = "RULE_BASED";
        setStage(run, "ANALYZE"); runs.save(run);
        return switch (intent) {
            case "GENERATE_PLAN" -> {
                setStage(run, "PLAN"); runs.save(run);
                var generated = execute(run, student, "generate_study_plan", "{}");
                if (!generated.success()) yield "Rule-based planning could not create a candidate: " + generated.error() + ". Add the missing subject, syllabus, or availability information and try again.";
                var plan = (edu.studyplanner.api.ApiModels.PlanDto)generated.data();
                if (!plan.feasible() || plan.id() == null) yield "Rule-based analysis found schedule risks. " + String.join("; ", plan.conflicts());
                setStage(run, "EVALUATE"); runs.save(run);
                execute(run, student, "validate_study_plan", "{\"planId\":\"" + plan.id() + "\"}");
                setStage(run, "ACT"); runs.save(run);
                execute(run, student, "create_study_tasks", "{\"planId\":\"" + plan.id() + "\"}");
                yield "Rule-based draft created with " + plan.tasks().size() + " sessions and checked by Java schedule validation. Review and approve it to activate the tasks.";
            }
            case "ADAPT_PLAN" -> {
                setStage(run, "PLAN"); runs.save(run);
                setStage(run, "ADAPT"); runs.save(run);
                var proposal = execute(run, student, "propose_reschedule", "{}");
                if (!proposal.success()) yield "Rule-based rescheduling could not create a proposal: " + proposal.error();
                yield "Rule-based revision proposal created. Review the moved sessions and approve or reject it; the active schedule has not changed.";
            }
            default -> {
                setStage(run, "EVALUATE"); runs.save(run);
                yield "Progress review complete. Your task totals and upcoming work are shown in the dashboard.";
            }
        };
    }
    private String enforcePostconditions(AgentRun run, UUID student, String intent, String previousSummary) {
        var runTools = executions.findAllByRunIdOrderByCreatedAtAsc(run.id);
        if ("GENERATE_PLAN".equals(intent)) {
            boolean generated = runTools.stream().anyMatch(t -> "generate_study_plan".equals(t.toolName) && "SUCCEEDED".equals(t.status));
            if (generated) {
                var draft = plans.findFirstByStudentIdAndStatusOrderByCreatedAtDesc(student, "DRAFT");
                if (draft.isPresent()) {
                    AgentToolRegistry.ToolResult validation = execute(run, student, "validate_study_plan", "{\"planId\":\"" + draft.get().id + "\"}");
                    if (!validation.success()) return "The candidate plan could not be validated. It remains inactive until a valid schedule is available.";
                    if (validation.data() instanceof java.util.Map<?,?> result && Boolean.FALSE.equals(result.get("feasible")))
                        return "The candidate plan has schedule conflicts and was not prepared for approval. Review your available hours and exam dates.";
                    boolean prepared = runTools.stream().anyMatch(t -> "create_study_tasks".equals(t.toolName) && "SUCCEEDED".equals(t.status));
                    if (!prepared) execute(run, student, "create_study_tasks", "{\"planId\":\"" + draft.get().id + "\"}");
                } else return "The planning tools did not save a feasible candidate. No tasks were activated; adjust your syllabus, study time, or exam dates and try again.";
            } else { run.mode = "RULE_BASED"; return fallback(run, student, intent); }
        }
        if ("ADAPT_PLAN".equals(intent) && runTools.stream().noneMatch(t -> "propose_reschedule".equals(t.toolName) && "SUCCEEDED".equals(t.status))) {
            run.mode = "RULE_BASED"; return fallback(run, student, intent);
        }
        return previousSummary;
    }
    public void applyApprovedRevision(UUID studentId, UUID revisionId) {
        AgentRun run = runs.save(new AgentRun(studentId, "ACT", "RUNNING", "RULE_BASED", "Applying a revision the student explicitly approved."));
        run.stageHistory = "OBSERVE,ANALYZE,PLAN,ACT";
        var result = execute(run, studentId, "apply_approved_reschedule", "{\"revisionId\":\"" + revisionId + "\"}");
        if (!result.success()) {
            run.status = "FAILED"; run.summary = "The approved revision could not be applied: " + result.error(); runs.save(run);
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, run.summary);
        }
        setStage(run, "RECORD"); run.status = "COMPLETED"; run.summary = "Student approval recorded and the approved revision was applied."; runs.save(run);
    }
    private String stageFor(String tool) {
        if (tool.startsWith("get_") || tool.equals("parse_syllabus")) return "OBSERVE";
        if (tool.equals("estimate_topic_duration")) return "ANALYZE";
        if (tool.equals("generate_study_plan") || tool.equals("propose_reschedule")) return "PLAN";
        if (tool.startsWith("create_") || tool.startsWith("apply_")) return "ACT";
        if (tool.startsWith("validate_")) return "EVALUATE";
        return "ADAPT";
    }
    private void setStage(AgentRun run, String stage) {
        run.stage = stage;
        java.util.LinkedHashSet<String> history = new java.util.LinkedHashSet<>(java.util.Arrays.asList(run.stageHistory.split(",")));
        history.add(stage); run.stageHistory = String.join(",", history);
    }
    private static final class ListActions {
        static boolean contains(String s) { return java.util.Set.of("generate_study_plan", "create_study_tasks", "propose_reschedule", "apply_approved_reschedule").contains(s); }
    }
}

