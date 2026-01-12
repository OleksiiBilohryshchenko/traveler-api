package ua.sumdu.dds.travelerapi.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ua.sumdu.dds.travelerapi.dto.MetadataUpdateRequest;
import ua.sumdu.dds.travelerapi.dto.TravelPlanSearchRequest;
import ua.sumdu.dds.travelerapi.model.TravelPlan;
import ua.sumdu.dds.travelerapi.service.TravelPlanService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for Travel Plan JSONB metadata operations.
 *
 * Base path: /api/plans/{planId}/metadata
 *
 * Note: /by-tags endpoint was removed due to PostgreSQL ?| operator
 * conflict with JDBC parameter placeholders.
 */
@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class TravelPlanMetadataController {

    private final TravelPlanService service;

    /**
     * Get metadata for a travel plan.
     *
     * GET /api/plans/{planId}/metadata
     *
     * Response: 200 OK
     * {
     *   "preferences": {
     *     "travel_style": "adventure",
     *     "budget_category": "moderate"
     *   },
     *   "tags": ["europe", "summer"]
     * }
     */
    @GetMapping("/{planId}/metadata")
    public ResponseEntity<Map<String, Object>> getMetadata(@PathVariable UUID planId) {
        Map<String, Object> metadata = service.getMetadata(planId);
        return ResponseEntity.ok(metadata);
    }

    /**
     * Update metadata (merge or replace).
     *
     * PATCH /api/plans/{planId}/metadata
     *
     * Request body:
     * {
     *   "version": 5,
     *   "metadata": {
     *     "tags": ["beach", "relaxation"]
     *   },
     *   "merge": true
     * }
     *
     * Response: 200 OK with updated TravelPlan
     */
    @PatchMapping("/{planId}/metadata")
    public ResponseEntity<TravelPlan> updateMetadata(
            @PathVariable UUID planId,
            @Valid @RequestBody MetadataUpdateRequest request) {

        TravelPlan updated = service.updateMetadata(planId, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * Delete specific key from metadata.
     *
     * DELETE /api/plans/{planId}/metadata/{key}?version=5
     *
     * Response: 200 OK with updated TravelPlan
     */
    @DeleteMapping("/{planId}/metadata/{key}")
    public ResponseEntity<TravelPlan> deleteMetadataKey(
            @PathVariable UUID planId,
            @PathVariable String key,
            @RequestParam Integer version) {

        TravelPlan updated = service.deleteMetadataKey(planId, key, version);
        return ResponseEntity.ok(updated);
    }

    /**
     * Search travel plans with JSONB filters.
     *
     * GET /api/plans/search?travel_style=adventure&budget_category=moderate&is_public=true
     *
     * Query parameters:
     * - travel_style: Filter by metadata->preferences->travel_style
     * - budget_category: Filter by metadata->preferences->budget_category
     * - pace: Filter by metadata->preferences->pace (currently not supported in search)
     * - is_public: Boolean filter
     *
     * Note: tags parameter removed due to PostgreSQL operator conflict.
     *
     * Response: 200 OK with array of TravelPlan
     */
    @GetMapping("/search")
    public ResponseEntity<List<TravelPlan>> searchPlans(
            @RequestParam(required = false, name = "travel_style") String travelStyle,
            @RequestParam(required = false, name = "budget_category") String budgetCategory,
            @RequestParam(required = false) String pace,
            @RequestParam(required = false, name = "is_public") Boolean isPublic) {

        TravelPlanSearchRequest searchRequest = new TravelPlanSearchRequest(
                travelStyle,
                budgetCategory,
                pace,
                null, // tags not supported
                isPublic
        );

        List<TravelPlan> results = service.searchPlans(searchRequest);
        return ResponseEntity.ok(results);
    }

    /**
     * Find travel plans by travel style.
     *
     * GET /api/plans/by-travel-style/{travelStyle}
     *
     * Example: GET /api/plans/by-travel-style/adventure
     *
     * Response: 200 OK with array of TravelPlan
     */
    @GetMapping("/by-travel-style/{travelStyle}")
    public ResponseEntity<List<TravelPlan>> findByTravelStyle(@PathVariable String travelStyle) {
        List<TravelPlan> results = service.findByTravelStyle(travelStyle);
        return ResponseEntity.ok(results);
    }

    /**
     * Find travel plans by budget category.
     *
     * GET /api/plans/by-budget-category/{budgetCategory}
     *
     * Example: GET /api/plans/by-budget-category/moderate
     *
     * Response: 200 OK with array of TravelPlan
     */
    @GetMapping("/by-budget-category/{budgetCategory}")
    public ResponseEntity<List<TravelPlan>> findByBudgetCategory(@PathVariable String budgetCategory) {
        List<TravelPlan> results = service.findByBudgetCategory(budgetCategory);
        return ResponseEntity.ok(results);
    }
}