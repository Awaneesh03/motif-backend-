package com.motif.ideaforge.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.motif.ideaforge.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;
import java.util.Map;

/**
 * Spring MVC interceptor that enforces per-user rate limits on AI endpoints.
 *
 * Endpoint → rate-limit key mapping:
 *   POST /api/analysis/start          → ai-analyze
 *   POST /api/ai/analyze-idea         → ai-analyze
 *   POST /api/ai/analyze              → ai-analyze
 *   POST /api/ai/generate-idea        → ai-generate
 *   POST /api/ai/generate-pitch       → ai-generate
 *   POST /api/ai/chat                 → ai-chat
 *   POST /api/ai/chat/stream          → ai-chat
 *   POST /api/ai/mentor-chat          → ai-chat
 *   POST /api/ai/mentor-chat/stream   → ai-chat
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        String endpointKey = resolveEndpointKey(request);
        if (endpointKey == null) return true; // not a rate-limited endpoint

        String userId = extractUserId();
        if (userId == null) return true; // unauthenticated — let Spring Security handle it

        if (!rateLimitService.isAllowed(userId, endpointKey)) {
            int remaining = rateLimitService.remaining(userId, endpointKey);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
            response.setHeader("Retry-After", "3600");

            Map<String, Object> body = Map.of(
                    "timestamp", Instant.now().toString(),
                    "status", 429,
                    "error", "Too Many Requests",
                    "message", "Rate limit exceeded. You may make " +
                            rateLimitService.remaining(userId, endpointKey) +
                            " more requests of this type per hour.",
                    "errorCode", "RATE_LIMIT_EXCEEDED"
            );
            response.getWriter().write(objectMapper.writeValueAsString(body));
            return false;
        }

        // Add remaining-quota header to successful responses
        response.setHeader("X-RateLimit-Remaining",
                String.valueOf(rateLimitService.remaining(userId, endpointKey)));
        return true;
    }

    private String resolveEndpointKey(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) return null;

        String path = request.getRequestURI();
        if (path.startsWith("/api/analysis/start")
                || path.equals("/api/ai/analyze-idea")
                || path.equals("/api/ai/analyze")) {
            return "ai-analyze";
        }
        if (path.equals("/api/ai/generate-idea")
                || path.equals("/api/ai/generate-pitch")) {
            return "ai-generate";
        }
        if (path.startsWith("/api/ai/chat")
                || path.startsWith("/api/ai/mentor-chat")
                || path.equals("/api/ai/improve-description")) {
            return "ai-chat";
        }
        return null;
    }

    private String extractUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        return auth.getName(); // set by JwtAuthenticationFilter as the user's UUID
    }
}
