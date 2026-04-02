package com.motif.ideaforge.controller;

import com.motif.ideaforge.model.dto.request.ContactRequest;
import com.motif.ideaforge.service.EmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public endpoint — no authentication required.
 * Whitelisted explicitly in SecurityConfig before the /api/** authenticated rule.
 */
@RestController
@RequestMapping("/api/contact")
@RequiredArgsConstructor
@Slf4j
public class ContactController {

    private final EmailService emailService;

    @PostMapping
    public ResponseEntity<Map<String, String>> send(@Valid @RequestBody ContactRequest request) {
        try {
            emailService.sendContactEmail(
                request.getName().trim(),
                request.getEmail().trim(),
                request.getMessage().trim()
            );
            return ResponseEntity.ok(Map.of("message", "Message sent successfully"));
        } catch (Exception e) {
            log.error("[ContactController] Failed to send contact email: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("message", "Failed to send message. Please try again later."));
        }
    }
}
