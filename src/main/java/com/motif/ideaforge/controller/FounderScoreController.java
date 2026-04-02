package com.motif.ideaforge.controller;

import com.motif.ideaforge.model.dto.response.FounderScoreResponse;
import com.motif.ideaforge.security.UserPrincipal;
import com.motif.ideaforge.service.FounderScoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/founder")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Founder", description = "Founder scoring and profile endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class FounderScoreController {

    private final FounderScoreService founderScoreService;

    /**
     * Returns a data-driven founder score (0–100) for the authenticated user.
     * Scores: profile quality, activity signals, consistency, engagement depth.
     */
    @GetMapping("/score")
    @Operation(summary = "Get the authenticated founder's score and breakdown")
    public ResponseEntity<FounderScoreResponse> getScore(
            @AuthenticationPrincipal UserPrincipal user) {

        log.debug("getFounderScore — user={}", user.getId());
        return ResponseEntity.ok(founderScoreService.calculateScore(user.getId()));
    }
}
