package edu.studyplanner.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(title = "AI Study Planner Agent API", version = "v1", description = "Student-owned study planning, scheduling, progress, and controlled agent tools."))
public class OpenApiConfig {}

