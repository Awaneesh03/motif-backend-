package com.motif.ideaforge.controller;

import com.motif.ideaforge.model.dto.response.RecentActivityResponse;
import com.motif.ideaforge.security.UserPrincipal;
import com.motif.ideaforge.service.ai.IdeaAnalyzerService;
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

import java.util.List;

@RestController
@RequestMapping("/api/ideas")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Ideas", description = "User idea management")
@SecurityRequirement(name = "Bearer Authentication")
public class IdeaController {

    private final IdeaAnalyzerService ideaAnalyzerService;

    /**
     * Returns up to 10 of the authenticated user's most recently analyzed ideas,
     * ordered by last-analyzed time (updated_at DESC).
     * Used by the dashboard "Recent Activity" panel.
     */
    @GetMapping("/recent")
    @Operation(summary = "Get recently analyzed ideas for the logged-in user")
    public ResponseEntity<List<RecentActivityResponse>> getRecentActivity(
            @AuthenticationPrincipal UserPrincipal user) {

        log.debug("Recent activity request — user: {}", user.getId());
        List<RecentActivityResponse> activity = ideaAnalyzerService.getRecentActivity(user.getId());
        return ResponseEntity.ok(activity);
    }
}
