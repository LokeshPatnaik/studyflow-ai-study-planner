package edu.studyplanner.service;

import edu.studyplanner.api.ApiModels.PreferenceDto;
import edu.studyplanner.domain.StudentPreference;
import edu.studyplanner.repository.PreferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Arrays;

@Service
public class PreferenceService {
    private final PreferenceRepository preferences;
    public PreferenceService(PreferenceRepository preferences) { this.preferences = preferences; }
    @Transactional(readOnly = true) public StudentPreference get(java.util.UUID studentId) {
        return preferences.findById(studentId).orElseGet(() -> new StudentPreference(studentId));
    }
    @Transactional public StudentPreference update(java.util.UUID studentId, PreferenceDto dto) {
        StudentPreference p = preferences.findById(studentId).orElseGet(() -> new StudentPreference(studentId));
        if (dto.dayEnd().isBefore(dto.dayStart()) || dto.dayEnd().equals(dto.dayStart()))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Study day must end after it starts");
        p.dailyStudyMinutes = dto.dailyStudyMinutes(); p.sessionMinutes = dto.sessionMinutes(); p.breakMinutes = dto.breakMinutes();
        p.preferredDays = String.join(",", dto.preferredDays().stream().map(String::toUpperCase).distinct().toList());
        p.dayStart = dto.dayStart(); p.dayEnd = dto.dayEnd(); p.revisionPreference = dto.revisionPreference().toUpperCase();
        return preferences.save(p);
    }
    public PreferenceDto toDto(StudentPreference p) {
        return new PreferenceDto(p.dailyStudyMinutes, p.sessionMinutes, p.breakMinutes,
                Arrays.asList(p.preferredDays.split(",")), p.dayStart, p.dayEnd, p.revisionPreference);
    }
}

