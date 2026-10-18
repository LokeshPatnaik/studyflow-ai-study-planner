package edu.studyplanner.api;

import edu.studyplanner.domain.StudentPreference;
import edu.studyplanner.security.CurrentStudent;
import edu.studyplanner.service.PreferenceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import static edu.studyplanner.api.ApiModels.PreferenceDto;

@RestController @RequestMapping("/api/v1/preferences")
public class PreferenceController {
    private final CurrentStudent current; private final PreferenceService service;
    public PreferenceController(CurrentStudent current, PreferenceService service) { this.current = current; this.service = service; }
    @GetMapping public PreferenceDto get() { return service.toDto(service.get(current.get().id)); }
    @PutMapping public PreferenceDto update(@Valid @RequestBody PreferenceDto dto) { StudentPreference p = service.update(current.get().id, dto); return service.toDto(p); }
}

