package ua.sumdu.dds.travelerapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request DTO for searching locations with JSONB attributes filters.
 * 
 * Examples:
 * 
 * 1. Search by category:
 * GET /api/locations/search?category=museum
 * 
 * 2. Search by minimum rating:
 * GET /api/locations/search?minRating=4.5
 * 
 * 3. Search by tags (ANY match):
 * GET /api/locations/search?tags=historical,art
 * 
 * 4. Search by accessibility features:
 * GET /api/locations/search?accessibility=wheelchair
 * 
 * 5. Combined search:
 * GET /api/locations/search?category=museum&minRating=4.0&tags=historical
 */
public record LocationSearchRequest(
        
        /**
         * Filter by attributes->category
         */
        String category,
        
        /**
         * Filter by attributes->rating (minimum value)
         */
        @JsonProperty("min_rating")
        BigDecimal minRating,
        
        /**
         * Filter by attributes->tags (ANY match, array intersection)
         * Example: ["historical", "art"] matches locations with ANY of these tags
         */
        List<String> tags,
        
        /**
         * Filter by attributes->accessibility (contains check)
         * Example: "wheelchair" matches if accessibility array contains "wheelchair"
         */
        String accessibility
) {}