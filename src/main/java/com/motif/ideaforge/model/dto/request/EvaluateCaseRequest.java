package com.motif.ideaforge.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for evaluating a business case solution
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluateCaseRequest {

    @NotBlank(message = "Case title is required")
    private String caseTitle;

    @NotBlank(message = "Company name is required")
    private String company;

    @NotBlank(message = "Problem statement is required")
    private String problem;

    @NotBlank(message = "Solution is required")
    @Size(min = 50, max = 5000, message = "Solution must be between 50 and 5000 characters")
    private String solution;
}
