package ua.sumdu.dds.travelerapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Request DTO for updating travel plan metadata (JSONB field).
 * 
 * Supports both full replacement and partial merge operations.
 * 
 * Example full replacement:
 * {
 *   "version": 5,
 *   "metadata": {
 *     "preferences": {"budget_category": "luxury"},
 *     "tags": ["honeymoon", "europe"]
 *   },
 *   "merge": false
 * }
 * 
 * Example partial merge (preserves other keys):
 * {
 *   "version": 5,
 *   "metadata": {
 *     "tags": ["beach", "relaxation"]
 *   },
 *   "merge": true
 * }
 */
public record MetadataUpdateRequest(
        
        @NotNull(message = "Version is required for optimistic locking")
        Integer version,
        
        @NotNull(message = "Metadata cannot be null")
        Map<String, Object> metadata,
        
        /**
         * If true: merge with existing metadata (shallow merge at root level).
         * If false: replace entire metadata object.
         * Default: true
         */
        @JsonProperty("merge")
        Boolean merge
) {
    public MetadataUpdateRequest {
        // Default merge to true if not specified
        if (merge == null) {
            merge = true;
        }
    }
}