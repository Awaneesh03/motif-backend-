package com.motif.ideaforge.model.dto.response;

import com.motif.ideaforge.model.entity.IdeaAnalysis;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for idea analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResponse {

    private String analysisId;
    private Integer score;
    private List<String> strengths;
    private List<String> weaknesses;
    private List<String> recommendations;
    private String marketSize;
    private String competition;
    private String viability;
    private Instant timestamp;

    public static AnalysisResponse fromEntity(IdeaAnalysis entity) {
        return AnalysisResponse.builder()
                .analysisId(entity.getId().toString())
                .score(entity.getScore())
                .strengths(entity.getStrengths())
                .weaknesses(entity.getWeaknesses())
                .recommendations(entity.getRecommendations())
                .marketSize(entity.getMarketSize())
                .competition(entity.getCompetition())
                .viability(entity.getViability())
                .timestamp(entity.getCreatedAt())
                .build();
    }
}
