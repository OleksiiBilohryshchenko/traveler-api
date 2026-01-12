package ua.sumdu.dds.travelerapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Request DTO for updating location attributes (JSONB field).
 * 
 * Supports both full replacement and partial merge operations.
 * 
 * Example full replacement:
 * {
 *   "version": 3,
 *   "attributes": {
 *     "rating": 4.8,
 *     "category": "restaurant",
 *     "contact": {
 *       "phone": "+1234567890"
 *     }
 *   },
 *   "merge": false
 * }
 * 
 * Example partial merge (preserves other keys):
 * {
 *   "version": 3,
 *   "attributes": {
 *     "rating": 4.9
 *   },
 *   "merge": true
 * }
 */
public record AttributesUpdateRequest(
        
        @NotNull(message = "Version is required for optimistic locking")
        Integer version,
        
        @NotNull(message = "Attributes cannot be null")
        Map<String, Object> attributes,
        
        /**
         * If true: merge with existing attributes (shallow merge at root level).
         * If false: replace entire attributes object.
         * Default: true
         */
        @JsonProperty("merge")
        Boolean merge
) {
    public AttributesUpdateRequest {
        // Default merge to true if not specified
        if (merge == null) {
            merge = true;
        }
    }
}