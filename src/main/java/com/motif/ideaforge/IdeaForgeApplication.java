package com.motif.ideaforge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main application class for Idea Forge Backend
 *
 * This Spring Boot application provides REST APIs for the Motif platform,
 * handling all business logic including AI-powered idea analysis, chatbot
 * functionality, and user management.
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableAsync
public class IdeaForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdeaForgeApplication.class, args);
    }

}
