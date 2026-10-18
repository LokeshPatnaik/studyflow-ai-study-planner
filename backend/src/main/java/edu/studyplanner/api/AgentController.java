package edu.studyplanner.api;

import edu.studyplanner.agent.AgentService;
import edu.studyplanner.agent.AgentToolRegistry;
import edu.studyplanner.security.CurrentStudent;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static edu.studyplanner.api.ApiModels.*;

@RestController @RequestMapping("/api/v1/agent")
public class AgentController {
    private final AgentService agent; private final AgentToolRegistry tools; private final CurrentStudent current;
    public AgentController(AgentService agent, AgentToolRegistry tools, CurrentStudent current) { this.agent=agent; this.tools=tools; this.current=current; }
    @PostMapping("/run") public AgentDto run(@Valid @RequestBody AgentRequest request) { return agent.run(current.get().id, request.intent(), request.note()); }
    @GetMapping("/activity") public List<AgentDto> activity() { return tools.runs(current.get().id); }
    @GetMapping("/tools") public List<ToolDto> toolHistory() { return tools.history(current.get().id); }
}

