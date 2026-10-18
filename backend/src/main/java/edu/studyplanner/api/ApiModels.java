package edu.studyplanner.api;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class ApiModels {
    private ApiModels() {}
    public record RegisterRequest(@NotBlank @Size(max = 100) String name, @NotBlank @Email @Size(max = 254) String email,
                                  @NotBlank @Size(min = 10, max = 100) String password) {}
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
    public record StudentDto(UUID id, String name, String email, boolean demo) {}
    public record AuthResponse(StudentDto student) {}
    public record PreferenceDto(@Min(30) @Max(720) int dailyStudyMinutes, @Min(20) @Max(180) int sessionMinutes,
                                @Min(0) @Max(60) int breakMinutes, @NotEmpty List<@Pattern(regexp = "(?i)MON|TUE|WED|THU|FRI|SAT|SUN") String> preferredDays,
                                @NotNull LocalTime dayStart, @NotNull LocalTime dayEnd,
                                @Pattern(regexp = "(?i)SPACED|PRACTICE|BALANCED") String revisionPreference) {}
    public record SubjectRequest(@NotBlank @Size(max = 120) String name, @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String color,
                                 @Min(1) @Max(5) int priority, @NotBlank String examName, @NotNull LocalDate examDate) {}
    public record SubjectDto(UUID id, String name, String color, int priority, String examName, LocalDate examDate,
                             long topicCount, long completedTopics) {}
    public record TopicRequest(@NotNull UUID subjectId, @NotBlank @Size(max = 180) String title, @Size(max = 120) String unit,
                               @Size(max = 120) String chapter, @Size(max = 1000) String description,
                               @Pattern(regexp = "(?i)EASY|MEDIUM|HARD") String difficulty,
                               @Min(10) @Max(720) Integer estimatedMinutes, @Min(1) @Max(5) Integer importance,
                               @Min(1) @Max(5) Integer familiarity, Boolean included, List<SourceText> sourceContent) {}
    public record TopicDto(UUID id, UUID subjectId, String title, String unit, String chapter, String description,
                           String difficulty, int estimatedMinutes, int importance, int familiarity, boolean included,
                           boolean approved, String originalTitle, String originalUnit, List<SourceText> sourceContent) {}
    public record ExamRequest(@NotNull UUID subjectId, @NotBlank String name, @NotNull LocalDate examDate) {}
    public record ExamDto(UUID id, UUID subjectId, String name, LocalDate examDate) {}
    public record PlanRequest(@Size(max = 120) String title, @Size(max = 50) List<UUID> subjectIds,
                              @Size(max = 366) List<LocalDate> unavailableDates,
                              @Size(max = 100) List<@Valid CommitmentRequest> commitments,
                              @Size(max = 64) String timeZone) {}
    public record CommitmentRequest(@NotNull LocalDate date, @NotNull LocalTime startTime, @NotNull LocalTime endTime,
                                    @Size(max = 120) String label) {}
    public record WorkloadTopicDto(UUID topicId, UUID subjectId, String subjectName, String title, LocalDate examDate,
                                   int importance, int learningMinutes, int practiceMinutes, int revisionMinutes,
                                   int totalMinutes, int scheduledMinutes, int unscheduledMinutes) {}
    public record WorkloadDto(int estimatedMinutes, int availableCapacityMinutes, int bufferMinutes,
                              int scheduledMinutes, int shortfallMinutes, List<WorkloadTopicDto> topics) {}
    public record ProposedTask(UUID taskId, UUID subjectId, UUID topicId, String subjectName, String title, String description,
                               LocalDate scheduledDate, LocalTime startTime, LocalTime endTime,
                               int durationMinutes, String priority, LocalDate examDate) {}
    public record PlanDto(UUID id, String title, String status, String aiMode, String summary,
                          boolean feasible, List<String> conflicts, List<TaskDto> tasks, WorkloadDto workload) {
        public PlanDto(UUID id, String title, String status, String aiMode, String summary,
                       boolean feasible, List<String> conflicts, List<TaskDto> tasks) {
            this(id, title, status, aiMode, summary, feasible, conflicts, tasks, null);
        }
    }
    public record TaskDto(UUID id, UUID planId, UUID subjectId, UUID topicId, String subjectName, String subjectColor,
                          String title, String description, LocalDate scheduledDate, LocalTime startTime, LocalTime endTime,
                          int durationMinutes, String priority, String status, LocalDate examDate, String sessionType,
                          int revisionNumber, Integer actualMinutes, String notes, Integer confidence) {}
    public record TaskScheduleRequest(@NotNull LocalDate scheduledDate, @NotNull LocalTime startTime,
                                      @Min(15) @Max(360) Integer durationMinutes) {}
    public record PlanStatusRequest(@NotBlank @Pattern(regexp = "(?i)ACTIVE|PAUSED|ARCHIVED") String status) {}
    public record TaskStatusRequest(@NotBlank @Pattern(regexp = "(?i)COMPLETED|MISSED|PLANNED") String status) {}
    public record RevisionRequest(@Size(max = 600) String reason) {}
    public record RevisionDto(UUID id, UUID planId, String status, String reason, List<ProposedTask> proposedTasks,
                              List<TaskDto> originalTasks, String createdAt) {}
    public record ProgressDto(long total, long completed, long pending, long missed, int completionPercent,
                              List<SubjectProgress> subjects, List<DayProgress> week) {}
    public record SubjectProgress(UUID subjectId, String subjectName, String color, long total, long completed, int percent) {}
    public record DayProgress(String day, int plannedMinutes, int completedMinutes) {}
    public record AgentRequest(@NotBlank @Pattern(regexp = "(?i)GENERATE_PLAN|ADAPT_PLAN|REVIEW_PROGRESS") String intent,
                               @Size(max = 400) String note) {}
    public record AgentDto(UUID id, String stage, String status, String mode, String summary, String createdAt, List<String> stageHistory,
                           List<ToolDto> tools) {}
    public record ToolDto(String name, String status, String inputSummary, String outputSummary, String errorMessage, String createdAt) {}
    public record SyllabusUploadDto(boolean parsed, boolean requiresOcr, String message, String extractedText,
                                    List<ParsedTopic> topics) {}
    public record SourceText(@NotBlank @Size(max = 2000) String text, @Min(1) Integer page, boolean heading) {}
    public record ParsedTopic(@NotBlank @Size(max = 180) String title, @Size(max = 120) String unit,
                              @Size(max = 120) String chapter, @Size(max = 1000) String description,
                              @Pattern(regexp = "(?i)EASY|MEDIUM|HARD") String difficulty,
                              @Min(15) @Max(720) int estimatedMinutes, @Min(1) @Max(5) Integer importance,
                              @Min(1) @Max(5) Integer familiarity, Boolean included,
                              List<@Valid SourceText> sourceContent) {
        public ParsedTopic(String title, String unit, String difficulty, int estimatedMinutes) {
            this(title, unit, null, null, difficulty, estimatedMinutes, 3, 3, true, List.of());
        }
    }
    public record AddTopicsRequest(@NotNull UUID subjectId, @Pattern(regexp = "(?i)MANUAL|PDF") String source, @Size(max = 180) String sourceName,
                                   @NotEmpty List<@Valid ParsedTopic> topics) {}
    public record SyllabusDto(UUID id, UUID subjectId, String subjectName, String sourceName, String sourceType,
                              int topicCount, String status, String createdAt) {}
    public record DecisionRequest(@NotBlank @Pattern(regexp = "(?i)APPROVE|REJECT") String decision) {}
    public record MessageDto(String message) {}
}

