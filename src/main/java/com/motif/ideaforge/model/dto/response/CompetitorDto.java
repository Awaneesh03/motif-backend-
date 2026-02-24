package com.motif.ideaforge.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single competitor entry returned by the AI analysis.
 * Used in the structured competitors list on the analysis response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompetitorDto {

    /** Real company or product name. */
    private String name;

    /** One specific capability the competitor has that this idea currently lacks. */
    private String threat;

    /** One specific gap in the competitor's offering that this idea could exploit. */
    private String opportunity;
}
