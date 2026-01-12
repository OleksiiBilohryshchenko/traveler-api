package ua.sumdu.dds.travelerapi.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ua.sumdu.dds.travelerapi.dto.AttributesUpdateRequest;
import ua.sumdu.dds.travelerapi.dto.LocationSearchRequest;
import ua.sumdu.dds.travelerapi.model.Location;
import ua.sumdu.dds.travelerapi.service.TravelPlanService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for Location JSONB attributes operations.
 * 
 * Base paths:
 * - /api/plans/{planId}/locations/{locationId}/attributes
 * - /api/locations/search
 */
@RestController
@RequiredArgsConstructor
public class LocationAttributesController {

    private final TravelPlanService service;

    /**
     * Get attributes for a location.
     * 
     * GET /api/plans/{planId}/locations/{locationId}/attributes
     * 
     * Response: 200 OK
     * {
     *   "rating": 4.5,
     *   "category": "museum",
     *   "contact": {
     *     "phone": "+33123456789"
     *   },
     *   "tags": ["historical", "art"]
     * }
     */
    @GetMapping("/api/plans/{planId}/locations/{locationId}/attributes")
    public ResponseEntity<Map<String, Object>> getAttributes(
            @PathVariable UUID planId,
            @PathVariable UUID locationId) {
        
        Map<String, Object> attributes = service.getLocationAttributes(planId, locationId);
        return ResponseEntity.ok(attributes);
    }

    /**
     * Update location attributes (merge or replace).
     * 
     * PATCH /api/plans/{planId}/locations/{locationId}/attributes
     * 
     * Request body:
     * {
     *   "version": 3,
     *   "attributes": {
     *     "rating": 4.9,
     *     "tags": ["updated", "new"]
     *   },
     *   "merge": true
     * }
     * 
     * Response: 200 OK with updated Location
     */
    @PatchMapping("/api/plans/{planId}/locations/{locationId}/attributes")
    public ResponseEntity<Location> updateAttributes(
            @PathVariable UUID planId,
            @PathVariable UUID locationId,
            @Valid @RequestBody AttributesUpdateRequest request) {
        
        Location updated = service.updateLocationAttributes(planId, locationId, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * Delete specific key from location attributes.
     * 
     * DELETE /api/plans/{planId}/locations/{locationId}/attributes/{key}?version=3
     * 
     * Response: 200 OK with updated Location
     */
    @DeleteMapping("/api/plans/{planId}/locations/{locationId}/attributes/{key}")
    public ResponseEntity<Location> deleteAttributeKey(
            @PathVariable UUID planId,
            @PathVariable UUID locationId,
            @PathVariable String key,
            @RequestParam Integer version) {
        
        Location updated = service.deleteLocationAttributeKey(planId, locationId, key, version);
        return ResponseEntity.ok(updated);
    }

    /**
     * Search locations with JSONB filters.
     * 
     * GET /api/locations/search?category=museum&min_rating=4.5&tags=historical,art
     * 
     * Query parameters:
     * - category: Filter by attributes->category
     * - min_rating: Filter by minimum attributes->rating
     * - tags: Comma-separated list, ANY match
     * - accessibility: Filter by accessibility array contains
     * 
     * Response: 200 OK with array of Location
     */
    @GetMapping("/api/locations/search")
    public ResponseEntity<List<Location>> searchLocations(
            @RequestParam(required = false) String category,
            @RequestParam(required = false, name = "min_rating") BigDecimal minRating,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(required = false) String accessibility) {

        LocationSearchRequest searchRequest = new LocationSearchRequest(
                category,
                minRating,
                tags,
                accessibility
        );

        List<Location> results = service.searchLocations(searchRequest);
        return ResponseEntity.ok(results);
    }

    /**
     * Find locations by category.
     * 
     * GET /api/locations/by-category/{category}
     * 
     * Example: GET /api/locations/by-category/museum
     * 
     * Response: 200 OK with array of Location
     */
    @GetMapping("/api/locations/by-category/{category}")
    public ResponseEntity<List<Location>> findByCategory(@PathVariable String category) {
        List<Location> results = service.findLocationsByCategory(category);
        return ResponseEntity.ok(results);
    }

    /**
     * Find top-rated locations within a travel plan.
     * 
     * GET /api/plans/{planId}/locations/top-rated?min_rating=4.0
     * 
     * Response: 200 OK with array of Location (sorted by rating DESC)
     */
    @GetMapping("/api/plans/{planId}/locations/top-rated")
    public ResponseEntity<List<Location>> findTopRated(
            @PathVariable UUID planId,
            @RequestParam(name = "min_rating", defaultValue = "4.0") BigDecimal minRating) {
        
        List<Location> results = service.findTopRatedLocations(planId, minRating);
        return ResponseEntity.ok(results);
    }
}