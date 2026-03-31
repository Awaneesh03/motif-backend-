package com.motif.ideaforge.model.dto.response;

import com.motif.ideaforge.model.entity.FundingQualification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FundingQualificationResponse {

    /** True when a saved qualification was found for this user. False when no row exists yet. */
    private boolean found;

    private String fullName;
    private String email;
    private String experienceLevel;
    private String linkedinUrl;
    private String previousStartups;
    private Instant updatedAt;

    /** Build a "found" response from a persisted entity. */
    public static FundingQualificationResponse from(FundingQualification entity) {
        return FundingQualificationResponse.builder()
                .found(true)
                .fullName(entity.getFullName())
                .email(entity.getEmail())
                .experienceLevel(entity.getExperienceLevel())
                .linkedinUrl(entity.getLinkedinUrl())
                .previousStartups(entity.getPreviousStartups())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /** Build a "not found" response. */
    public static FundingQualificationResponse notFound() {
        return FundingQualificationResponse.builder().found(false).build();
    }
}
