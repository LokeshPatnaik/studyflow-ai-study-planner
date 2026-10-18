package edu.studyplanner.agent;

import edu.studyplanner.api.ApiModels.ParsedTopic;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;

@Component
public class OpenAiFunctionCallingClient implements SyllabusTopicParser {
    private final ObjectMapper mapper; private final String apiKey, model; private final int timeoutSeconds;
    private final HttpClient http;
    public OpenAiFunctionCallingClient(ObjectMapper mapper, @Value("${app.openai.api-key:}") String apiKey,
            @Value("${app.openai.model:gpt-4o-mini}") String model, @Value("${app.openai.timeout-seconds:25}") int timeoutSeconds) {
        this.mapper=mapper; this.apiKey=apiKey; this.model=model; this.timeoutSeconds=timeoutSeconds;
        this.http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    }
    public boolean configured() { return apiKey != null && !apiKey.isBlank(); }
    public List<ParsedTopic> parseSyllabusTopics(String extractedText) throws Exception {
        if (!configured()) return List.of();
        ObjectNode parameters = (ObjectNode) mapper.readTree("""
                {"type":"object","properties":{"topics":{"type":"array","items":{"type":"object","properties":{"title":{"type":"string"},"unit":{"type":"string"},"difficulty":{"type":"string","enum":["EASY","MEDIUM","HARD"]},"estimatedMinutes":{"type":"integer"}},"required":["title","unit","difficulty","estimatedMinutes"],"additionalProperties":false}}},"required":["topics"],"additionalProperties":false}
                """);
        ArrayNode messages = mapper.createArrayNode();
        ObjectNode system = messages.addObject(); system.put("role", "system");
        system.put("content", "Extract only syllabus topics explicitly present in the supplied text. Keep their order and unit/module context, omit headings, references, and learning outcomes, infer difficulty conservatively, and estimate realistic study minutes. Do not invent topics.");
        ObjectNode user = messages.addObject(); user.put("role", "user"); user.put("content", extractedText);
        ArrayNode definitions = mapper.createArrayNode(); ObjectNode wrapper = definitions.addObject(); wrapper.put("type", "function");
        ObjectNode function = wrapper.putObject("function"); function.put("name", "parse_syllabus");
        function.put("description", "Return syllabus topics grounded in the provided extracted PDF text.");
        function.set("parameters", parameters); function.put("strict", true);
        ObjectNode body = mapper.createObjectNode(); body.put("model", model); body.set("messages", messages); body.set("tools", definitions);
        ObjectNode choice = body.putObject("tool_choice"); choice.put("type", "function"); choice.putObject("function").put("name", "parse_syllabus");
        body.put("parallel_tool_calls", false); body.put("max_completion_tokens", 1800);
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/chat/completions"))
                .timeout(Duration.ofSeconds(timeoutSeconds)).header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("OpenAI returned HTTP " + response.statusCode());
        JsonNode calls = mapper.readTree(response.body()).path("choices").path(0).path("message").path("tool_calls");
        if (!calls.isArray() || calls.isEmpty() || !"parse_syllabus".equals(calls.get(0).path("function").path("name").asText())) return List.of();
        JsonNode topics = mapper.readTree(calls.get(0).path("function").path("arguments").asText()).path("topics");
        if (!topics.isArray()) return List.of();
        List<ParsedTopic> result = new ArrayList<>();
        for (JsonNode item : topics) {
            String title = item.path("title").asText("").trim();
            if (title.length() < 4 || title.length() > 180) continue;
            String unit = item.path("unit").asText("").trim();
            String difficulty = item.path("difficulty").asText("MEDIUM").toUpperCase(Locale.ROOT);
            if (!List.of("EASY", "MEDIUM", "HARD").contains(difficulty)) difficulty = "MEDIUM";
            int minutes = item.path("estimatedMinutes").asInt(60);
            minutes = Math.max(15, Math.min(720, minutes));
            result.add(new ParsedTopic(title, unit.isBlank() ? null : unit.substring(0, Math.min(120, unit.length())), difficulty, minutes));
            if (result.size() == 80) break;
        }
        return result.stream().collect(java.util.stream.Collectors.collectingAndThen(
                java.util.stream.Collectors.toMap(t -> t.title().toLowerCase(Locale.ROOT), t -> t, (a,b) -> a, java.util.LinkedHashMap::new),
                map -> new ArrayList<>(map.values())));
    }
    public String complete(UUIDContext context, String intent, String note, List<AgentToolRegistry.ToolSpec<?>> specs,
                           java.util.function.BiFunction<String,String,AgentToolRegistry.ToolResult> executor) throws Exception {
        ArrayNode messages = mapper.createArrayNode();
        ObjectNode system = mapper.createObjectNode(); system.put("role", "system");
        system.put("content", "You are the controlled planning assistant for AI Study Planner Agent. The user is authenticated. Use only the provided typed tools; never infer private records. For GENERATE_PLAN, call get_student_preferences and get_exam_deadlines if needed, then generate_study_plan, validate_study_plan, and create_study_tasks for a feasible draft. For ADAPT_PLAN, inspect progress and call propose_reschedule; never apply it. For REVIEW_PROGRESS, use get_progress. Student approval is required for any significant change. Do not expose hidden reasoning; return a concise user-facing summary of completed tool actions and approval needs."); messages.add(system);
        ObjectNode user = mapper.createObjectNode(); user.put("role", "user");
        user.put("content", "Intent: " + intent + "\nStudent context is already bound securely by the backend: " + context.value + "\nUser note: " + (note == null ? "none" : note)); messages.add(user);
        ArrayNode toolDefinitions = mapper.createArrayNode();
        for (var spec : specs) {
            ObjectNode wrapper = toolDefinitions.addObject(); wrapper.put("type", "function"); ObjectNode fn = wrapper.putObject("function");
            fn.put("name", spec.name); fn.put("description", spec.description); fn.set("parameters", spec.parameters);
        }
        int invoked = 0; String finalMessage = "The agent completed its bounded tool run.";
        for (int round = 0; round < 4; round++) {
            ObjectNode body = mapper.createObjectNode(); body.put("model", model); body.set("messages", messages); body.set("tools", toolDefinitions);
            body.put("tool_choice", "required"); body.put("parallel_tool_calls", false); body.put("max_completion_tokens", 600);
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(timeoutSeconds)).header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("OpenAI returned HTTP " + response.statusCode());
            JsonNode choice = mapper.readTree(response.body()).path("choices").path(0).path("message");
            finalMessage = choice.path("content").asText("");
            JsonNode calls = choice.path("tool_calls");
            if (!calls.isArray() || calls.isEmpty()) break;
            messages.add(choice.deepCopy());
            for (JsonNode call : calls) {
                if (invoked >= 5) break;
                String id = call.path("id").asText(); String name = call.path("function").path("name").asText();
                String args = call.path("function").path("arguments").asText("{}");
                AgentToolRegistry.ToolResult result = executor.apply(name, args); invoked++;
                ObjectNode toolMessage = messages.addObject(); toolMessage.put("role", "tool"); toolMessage.put("tool_call_id", id);
                toolMessage.put("content", mapper.writeValueAsString(result));
            }
            if (invoked >= 5) break;
        }
        return finalMessage == null || finalMessage.isBlank() ? "The agent finished the available tool actions. Review any draft or proposal before approval." : finalMessage;
    }
    public record UUIDContext(String value) {}
}

