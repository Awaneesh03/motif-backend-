package com.motif.ideaforge.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratePitchRequest {
    @NotBlank(message = "Idea name is required")
    private String ideaName;
    @NotBlank(message = "Problem is required")
    private String problem;
    @NotBlank(message = "Solution is required")
    private String solution;
    private String audience;
    private String market;
    private String usp;
}
