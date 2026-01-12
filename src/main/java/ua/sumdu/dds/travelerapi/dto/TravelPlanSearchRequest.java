package ua.sumdu.dds.travelerapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Request DTO for searching travel plans with JSONB metadata filters.
 * 
 * Examples:
 * 
 * 1. Search by travel style:
 * GET /api/plans/search?travelStyle=adventure
 * 
 * 2. Search by tags (ANY match):
 * GET /api/plans/search?tags=europe,summer
 * 
 * 3. Search by budget category:
 * GET /api/plans/search?budgetCategory=moderate
 * 
 * 4. Combined search:
 * GET /api/plans/search?travelStyle=adventure&tags=europe&isPublic=true
 */
public record TravelPlanSearchRequest(
        
        /**
         * Filter by metadata->preferences->travel_style
         */
        @JsonProperty("travel_style")
        String travelStyle,
        
        /**
         * Filter by metadata->preferences->budget_category
         */
        @JsonProperty("budget_category")
        String budgetCategory,
        
        /**
         * Filter by metadata->preferences->pace
         */
        String pace,
        
        /**
         * Filter by metadata->tags (ANY match, array intersection)
         * Example: ["europe", "summer"] matches plans with ANY of these tags
         */
        List<String> tags,
        
        /**
         * Filter by is_public field (traditional column)
         */
        @JsonProperty("is_public")
        Boolean isPublic
) {}