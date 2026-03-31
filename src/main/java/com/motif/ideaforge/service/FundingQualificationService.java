package com.motif.ideaforge.service;

import com.motif.ideaforge.model.dto.request.FundingQualificationRequest;
import com.motif.ideaforge.model.dto.response.FundingQualificationResponse;
import com.motif.ideaforge.repository.FundingQualificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FundingQualificationService {

    private final FundingQualificationRepository repository;

    /**
     * Returns the saved qualification for this user, or a "not found" response
     * when no row exists yet (first-time user).
     */
    @Transactional(readOnly = true)
    public FundingQualificationResponse getByUserId(UUID userId) {
        return repository.findByUserId(userId)
                .map(FundingQualificationResponse::from)
                .orElse(FundingQualificationResponse.notFound());
    }

    /**
     * Upsert — creates a new row or updates the existing one.
     * After the upsert the updated row is fetched and returned so the
     * caller always gets the persisted state back.
     */
    @Transactional
    public FundingQualificationResponse upsert(UUID userId, FundingQualificationRequest request) {
        String linkedinUrl       = request.getLinkedinUrl()      != null ? request.getLinkedinUrl().trim()      : "";
        String previousStartups  = request.getPreviousStartups() != null ? request.getPreviousStartups().trim()  : "";

        repository.upsert(
                userId,
                request.getFullName().trim(),
                request.getEmail().trim(),
                request.getExperienceLevel().trim(),
                linkedinUrl,
                previousStartups
        );

        log.info("Funding qualification upserted for user {}", userId);

        // Fetch the persisted row to return the final state (including timestamps)
        return repository.findByUserId(userId)
                .map(FundingQualificationResponse::from)
                .orElseThrow(() -> new IllegalStateException(
                        "Qualification not found immediately after upsert for user: " + userId));
    }
}
