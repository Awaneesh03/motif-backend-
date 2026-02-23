package com.motif.ideaforge.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for the context-aware mentor chat endpoint.
 *
 * analysisId is optional — when null the service uses the user's latest analysis.
 * history   is optional — keep it to the last 6 messages on the frontend to avoid
 *           token bloat (the service enforces this cap server-side as well).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorChatRequest {

    @NotBlank(message = "message must not be blank")
    @Size(max = 2000, message = "message must be 2000 characters or fewer")
    private String message;

    /** UUID of a specific analysis to use. Null → use the user's latest analysis. */
    private String analysisId;

    /** Conversation history for multi-turn context. Capped to last 6 items server-side. */
    private List<HistoryItem> history;

    /** Opaque identifier for the frontend to correlate responses to conversations. */
    private String conversationId;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoryItem {
        /** "user" or "assistant" */
        private String role;
        private String content;
    }
}
