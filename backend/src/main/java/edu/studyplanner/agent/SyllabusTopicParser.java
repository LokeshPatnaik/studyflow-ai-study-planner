package edu.studyplanner.agent;

import edu.studyplanner.api.ApiModels.ParsedTopic;
import java.util.List;

public interface SyllabusTopicParser {
    boolean configured();
    List<ParsedTopic> parseSyllabusTopics(String extractedText) throws Exception;
}
