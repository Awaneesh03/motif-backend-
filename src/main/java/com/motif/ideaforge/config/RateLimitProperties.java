package com.motif.ideaforge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds rate-limit settings from application.yml (app.rate-limit.*).
 * All limits are expressed as max requests per hour per user.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;

    /** Max AI analyze calls per user per hour */
    private int aiAnalyze = 10;

    /** Max AI generate calls per user per hour */
    private int aiGenerate = 5;

    /** Max chat calls per user per hour */
    private int aiChat = 50;

    /** Max idea create operations per user per hour */
    private int ideaCreate = 20;

    /** Max idea update operations per user per hour */
    private int ideaUpdate = 50;
}
