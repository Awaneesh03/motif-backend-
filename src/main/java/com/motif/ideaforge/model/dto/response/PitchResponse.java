package com.motif.ideaforge.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PitchResponse {
    private List<SlideContent> slides;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlideContent {
        private String title;
        private List<String> points;
    }
}
