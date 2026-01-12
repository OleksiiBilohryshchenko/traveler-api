package ua.sumdu.dds.travelerapi.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.sumdu.dds.travelerapi.dto.*;
import ua.sumdu.dds.travelerapi.exception.NotFoundException;
import ua.sumdu.dds.travelerapi.exception.ValidationException;
import ua.sumdu.dds.travelerapi.exception.VersionConflictException;
import ua.sumdu.dds.travelerapi.model.Location;
import ua.sumdu.dds.travelerapi.model.TravelPlan;
import ua.sumdu.dds.travelerapi.repository.LocationRepository;
import ua.sumdu.dds.travelerapi.repository.TravelPlanRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TravelPlanService {

    private final TravelPlanRepository plans;
    private final LocationRepository locations;

    /* -------- Travel Plans -------- */

    public List<TravelPlan> listAll() {
        return plans.findAll();
    }

    public TravelPlan getById(UUID id) {
        return plans.findById(id)
                .orElseThrow(() -> new NotFoundException("Travel plan not found"));
    }

    @Transactional
    public TravelPlan create(CreateTravelPlanRequest r) {

        TravelPlan p = TravelPlan.builder()
                .title(r.title())
                .description(r.description())
                .startDate(r.startDate())
                .endDate(r.endDate())
                .budget(r.budget() != null ? r.budget() : BigDecimal.ZERO)
                .currency(r.currency() != null ? r.currency() : "USD")
                .isPublic(Boolean.TRUE.equals(r.isPublic()))
                .version(1)
                .metadata(new HashMap<>()) // Initialize empty metadata
                .build();

        return plans.save(p);
    }

    @Transactional
    public TravelPlan update(UUID id, UpdateTravelPlanRequest r) {
        TravelPlan p = getById(id); // 404

        LocalDate newStart = r.startDate() != null ? r.startDate() : p.getStartDate();
        LocalDate newEnd   = r.endDate() != null   ? r.endDate()   : p.getEndDate();

        if (newStart != null && newEnd != null && newEnd.isBefore(newStart)) {
            throw new IllegalArgumentException("end_date must be after or equal to start_date");
        }

        if (!p.getVersion().equals(r.version())) {
            throw new VersionConflictException(p.getVersion());
        }

        if (r.title() != null)       p.setTitle(r.title());
        if (r.description() != null) p.setDescription(r.description());
        if (r.startDate() != null)   p.setStartDate(r.startDate());
        if (r.endDate() != null)     p.setEndDate(r.endDate());
        if (r.budget() != null)      p.setBudget(r.budget());
        if (r.currency() != null)    p.setCurrency(r.currency());
        if (r.isPublic() != null)    p.setPublic(r.isPublic());

        p.setVersion(p.getVersion() + 1);

        return plans.save(p);
    }

    @Transactional
    public void delete(UUID id) {
        if (!plans.existsById(id)) {
            throw new NotFoundException("Travel plan not found");
        }
        plans.deleteById(id);
    }

    /* -------- JSONB Metadata Operations -------- */

    /**
     * Update travel plan metadata.
     *
     * Supports two modes:
     * 1. MERGE (default): Shallow merge at root level, preserves other keys
     * 2. REPLACE: Complete replacement of metadata object
     *
     * @param id Travel plan ID
     * @param request Metadata update request with merge flag
     * @return Updated travel plan
     */
    @Transactional
    public TravelPlan updateMetadata(UUID id, MetadataUpdateRequest request) {
        TravelPlan p = getById(id);

        // Optimistic locking check
        if (!p.getVersion().equals(request.version())) {
            throw new VersionConflictException(p.getVersion());
        }

        Map<String, Object> newMetadata;

        if (Boolean.TRUE.equals(request.merge())) {
            // MERGE mode: preserve existing keys, update only provided keys
            newMetadata = new HashMap<>(p.getMetadata());
            newMetadata.putAll(request.metadata());
        } else {
            // REPLACE mode: complete replacement
            newMetadata = new HashMap<>(request.metadata());
        }

        p.setMetadata(newMetadata);
        p.setVersion(p.getVersion() + 1);

        return plans.save(p);
    }

    /**
     * Get metadata for a travel plan.
     *
     * @param id Travel plan ID
     * @return Metadata map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getMetadata(UUID id) {
        TravelPlan p = getById(id);
        return p.getMetadata() != null ? p.getMetadata() : new HashMap<>();
    }

    /**
     * Delete specific key from metadata.
     *
     * @param id Travel plan ID
     * @param key Key to remove from metadata
     * @param version Current version for optimistic locking
     * @return Updated travel plan
     */
    @Transactional
    public TravelPlan deleteMetadataKey(UUID id, String key, Integer version) {
        TravelPlan p = getById(id);

        if (!p.getVersion().equals(version)) {
            throw new VersionConflictException(p.getVersion());
        }

        Map<String, Object> metadata = new HashMap<>(p.getMetadata());
        metadata.remove(key);

        p.setMetadata(metadata);
        p.setVersion(p.getVersion() + 1);

        return plans.save(p);
    }

    /* -------- JSONB Search Operations -------- */

    /**
     * Search travel plans by travel style.
     *
     * Note: Full searchWithFilters was removed due to PostgreSQL ?/?| operator
     * conflicts with JDBC. Use individual find methods instead.
     *
     * @param searchRequest Search criteria (only travelStyle and budgetCategory supported)
     * @return List of matching travel plans
     */
    @Transactional(readOnly = true)
    public List<TravelPlan> searchPlans(TravelPlanSearchRequest searchRequest) {
        // Start with travel style filter if provided
        if (searchRequest.travelStyle() != null) {
            List<TravelPlan> results = plans.findByTravelStyle(searchRequest.travelStyle());

            // Apply additional filters in Java
            return results.stream()
                    .filter(p -> matchesBudgetCategory(p, searchRequest.budgetCategory()))
                    .filter(p -> matchesPublicFlag(p, searchRequest.isPublic()))
                    .toList();
        }

        // Fall back to budget category if no travel style
        if (searchRequest.budgetCategory() != null) {
            List<TravelPlan> results = plans.findByBudgetCategory(searchRequest.budgetCategory());

            return results.stream()
                    .filter(p -> matchesPublicFlag(p, searchRequest.isPublic()))
                    .toList();
        }

        // If no JSONB filters, return all (optionally filtered by isPublic)
        List<TravelPlan> all = plans.findAll();
        if (searchRequest.isPublic() != null) {
            return all.stream()
                    .filter(p -> p.isPublic() == searchRequest.isPublic())
                    .toList();
        }
        return all;
    }

    private boolean matchesBudgetCategory(TravelPlan plan, String budgetCategory) {
        if (budgetCategory == null) return true;

        Map<String, Object> metadata = plan.getMetadata();
        if (metadata == null) return false;

        @SuppressWarnings("unchecked")
        Map<String, Object> preferences = (Map<String, Object>) metadata.get("preferences");
        if (preferences == null) return false;

        return budgetCategory.equals(preferences.get("budget_category"));
    }

    private boolean matchesPublicFlag(TravelPlan plan, Boolean isPublic) {
        if (isPublic == null) return true;
        return plan.isPublic() == isPublic;
    }

    /**
     * Find travel plans by specific travel style.
     */
    @Transactional(readOnly = true)
    public List<TravelPlan> findByTravelStyle(String travelStyle) {
        return plans.findByTravelStyle(travelStyle);
    }

    /**
     * Find travel plans by budget category.
     */
    @Transactional(readOnly = true)
    public List<TravelPlan> findByBudgetCategory(String budgetCategory) {
        return plans.findByBudgetCategory(budgetCategory);
    }

    /* -------- Locations -------- */

    @Transactional
    public Location addLocation(UUID planId, CreateLocationRequest r) {
        TravelPlan p = getById(planId);

        int nextOrder = locations.findByTravelPlan_IdOrderByVisitOrderAsc(planId)
                .stream()
                .map(Location::getVisitOrder)
                .max(Comparator.naturalOrder())
                .orElse(0) + 1;

        OffsetDateTime arrival = r.arrivalDate();
        OffsetDateTime departure = r.departureDate();

        if (r.arrivalDate() != null && r.departureDate() != null &&
                r.departureDate().isBefore(r.arrivalDate())) {
            throw new ValidationException(
                    List.of("departure_date must be after or equal to arrival_date")
            );
        }

        Location l = Location.builder()
                .travelPlan(p)
                .name(r.name())
                .address(r.address())
                .latitude(r.latitude())
                .longitude(r.longitude())
                .arrivalDate(arrival)
                .departureDate(departure)
                .budget(r.budget() != null ? r.budget() : BigDecimal.ZERO)
                .notes(r.notes())
                .visitOrder(nextOrder)
                .version(1)
                .attributes(new HashMap<>()) // Initialize empty attributes
                .build();

        return locations.save(l);
    }

    @Transactional
    public Location updateLocation(UUID planId,
                                   UUID locationId,
                                   UpdateLocationRequest r) {

        TravelPlan p = getById(planId);

        Location l = locations.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Location not found"));

        if (!l.getTravelPlan().getId().equals(p.getId())) {
            throw new NotFoundException("Location not found in travel plan");
        }

        if (r.version() != null && !l.getVersion().equals(r.version())) {
            throw new VersionConflictException(l.getVersion());
        }

        OffsetDateTime newArrival =
                r.arrivalDate() != null ? r.arrivalDate() : l.getArrivalDate();
        OffsetDateTime newDeparture =
                r.departureDate() != null ? r.departureDate() : l.getDepartureDate();

        if (newArrival != null && newDeparture != null &&
                newDeparture.isBefore(newArrival)) {
            throw new ValidationException(
                    List.of("departure_date must be after or equal to arrival_date")
            );
        }

        if (r.name() != null)      l.setName(r.name());
        if (r.address() != null)   l.setAddress(r.address());
        if (r.latitude() != null)  l.setLatitude(r.latitude());
        if (r.longitude() != null) l.setLongitude(r.longitude());
        if (r.budget() != null)    l.setBudget(r.budget());
        if (r.notes() != null)     l.setNotes(r.notes());

        l.setArrivalDate(newArrival);
        l.setDepartureDate(newDeparture);

        // optimistic locking: child + parent
        l.setVersion(l.getVersion() + 1);
        p.setVersion(p.getVersion() + 1);

        return l;
    }

    @Transactional
    public void deleteLocation(UUID planId,
                               UUID locationId) {

        TravelPlan p = getById(planId);

        Location l = locations.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Location not found"));

        if (!l.getTravelPlan().getId().equals(p.getId())) {
            throw new NotFoundException("Location not found in travel plan");
        }

        locations.delete(l);

        p.setVersion(p.getVersion() + 1);
    }

    public List<Location> listLocations(UUID planId) {
        getById(planId);
        return locations.findByTravelPlan_IdOrderByVisitOrderAsc(planId);
    }

    /* -------- JSONB Location Attributes Operations -------- */

    /**
     * Update location attributes.
     *
     * Supports two modes:
     * 1. MERGE (default): Shallow merge at root level, preserves other keys
     * 2. REPLACE: Complete replacement of attributes object
     *
     * @param planId Travel plan ID
     * @param locationId Location ID
     * @param request Attributes update request with merge flag
     * @return Updated location
     */
    @Transactional
    public Location updateLocationAttributes(UUID planId, UUID locationId, AttributesUpdateRequest request) {
        TravelPlan p = getById(planId);

        Location l = locations.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Location not found"));

        if (!l.getTravelPlan().getId().equals(p.getId())) {
            throw new NotFoundException("Location not found in travel plan");
        }

        // Optimistic locking check
        if (!l.getVersion().equals(request.version())) {
            throw new VersionConflictException(l.getVersion());
        }

        Map<String, Object> newAttributes;

        if (Boolean.TRUE.equals(request.merge())) {
            // MERGE mode: preserve existing keys, update only provided keys
            newAttributes = new HashMap<>(l.getAttributes());
            newAttributes.putAll(request.attributes());
        } else {
            // REPLACE mode: complete replacement
            newAttributes = new HashMap<>(request.attributes());
        }

        l.setAttributes(newAttributes);
        l.setVersion(l.getVersion() + 1);
        p.setVersion(p.getVersion() + 1);

        return locations.save(l);
    }

    /**
     * Get attributes for a location.
     *
     * @param planId Travel plan ID
     * @param locationId Location ID
     * @return Attributes map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getLocationAttributes(UUID planId, UUID locationId) {
        TravelPlan p = getById(planId);

        Location l = locations.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Location not found"));

        if (!l.getTravelPlan().getId().equals(p.getId())) {
            throw new NotFoundException("Location not found in travel plan");
        }

        return l.getAttributes() != null ? l.getAttributes() : new HashMap<>();
    }

    /**
     * Delete specific key from location attributes.
     *
     * @param planId Travel plan ID
     * @param locationId Location ID
     * @param key Key to remove from attributes
     * @param version Current version for optimistic locking
     * @return Updated location
     */
    @Transactional
    public Location deleteLocationAttributeKey(UUID planId, UUID locationId, String key, Integer version) {
        TravelPlan p = getById(planId);

        Location l = locations.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Location not found"));

        if (!l.getTravelPlan().getId().equals(p.getId())) {
            throw new NotFoundException("Location not found in travel plan");
        }

        if (!l.getVersion().equals(version)) {
            throw new VersionConflictException(l.getVersion());
        }

        Map<String, Object> attributes = new HashMap<>(l.getAttributes());
        attributes.remove(key);

        l.setAttributes(attributes);
        l.setVersion(l.getVersion() + 1);
        p.setVersion(p.getVersion() + 1);

        return locations.save(l);
    }

    /* -------- JSONB Location Search Operations -------- */

    /**
     * Search locations with JSONB attributes filters.
     *
     * Note: Full searchWithFilters was removed due to PostgreSQL ?/?| operator
     * conflicts with JDBC. Uses findByCategory + findByMinRating with Java filtering.
     *
     * @param searchRequest Search criteria
     * @return List of matching locations
     */
    @Transactional(readOnly = true)
    public List<Location> searchLocations(LocationSearchRequest searchRequest) {
        // Start with category filter if provided
        if (searchRequest.category() != null) {
            List<Location> results = locations.findByCategory(searchRequest.category());

            // Apply additional filters in Java
            return results.stream()
                    .filter(loc -> matchesMinRating(loc, searchRequest.minRating()))
                    .filter(loc -> matchesAccessibility(loc, searchRequest.accessibility()))
                    .toList();
        }

        // Fall back to minRating if no category
        if (searchRequest.minRating() != null) {
            List<Location> results = locations.findByMinRating(searchRequest.minRating());

            return results.stream()
                    .filter(loc -> matchesAccessibility(loc, searchRequest.accessibility()))
                    .toList();
        }

        // If only accessibility filter, get all and filter
        if (searchRequest.accessibility() != null) {
            return locations.findByAccessibilityFeature(searchRequest.accessibility());
        }

        // No filters - return empty (or could return all)
        return List.of();
    }

    private boolean matchesMinRating(Location location, BigDecimal minRating) {
        if (minRating == null) return true;

        Map<String, Object> attributes = location.getAttributes();
        if (attributes == null) return false;

        Object ratingObj = attributes.get("rating");
        if (ratingObj == null) return false;

        BigDecimal rating;
        if (ratingObj instanceof Number) {
            rating = new BigDecimal(ratingObj.toString());
        } else {
            return false;
        }

        return rating.compareTo(minRating) >= 0;
    }

    private boolean matchesAccessibility(Location location, String accessibility) {
        if (accessibility == null) return true;

        Map<String, Object> attributes = location.getAttributes();
        if (attributes == null) return false;

        Object accessibilityObj = attributes.get("accessibility");
        if (accessibilityObj instanceof List<?> list) {
            return list.contains(accessibility);
        }

        return false;
    }

    /**
     * Find locations by category.
     */
    @Transactional(readOnly = true)
    public List<Location> findLocationsByCategory(String category) {
        return locations.findByCategory(category);
    }

    /**
     * Find top-rated locations within a travel plan.
     */
    @Transactional(readOnly = true)
    public List<Location> findTopRatedLocations(UUID planId, BigDecimal minRating) {
        getById(planId); // Ensure plan exists
        return locations.findTopRatedInPlan(planId, minRating);
    }
}