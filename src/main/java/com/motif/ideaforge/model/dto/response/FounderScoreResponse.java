package com.motif.ideaforge.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FounderScoreResponse {

    private int    score;   // 0–100
    private String level;   // Beginner | Active | Strong | Investor Ready

    private Breakdown breakdown;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Breakdown {
        private int profile;     // max 40
        private int activity;    // max 30
        private int consistency; // max 20
        private int engagement;  // max 10
    }
}
