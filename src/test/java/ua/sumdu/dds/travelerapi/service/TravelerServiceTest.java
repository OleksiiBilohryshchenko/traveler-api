package ua.sumdu.dds.travelerapi.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ua.sumdu.dds.travelerapi.dto.AttributesUpdateRequest;
import ua.sumdu.dds.travelerapi.dto.CreateTravelPlanRequest;
import ua.sumdu.dds.travelerapi.dto.MetadataUpdateRequest;
import ua.sumdu.dds.travelerapi.dto.UpdateTravelPlanRequest;
import ua.sumdu.dds.travelerapi.exception.NotFoundException;
import ua.sumdu.dds.travelerapi.exception.VersionConflictException;
import ua.sumdu.dds.travelerapi.model.Location;
import ua.sumdu.dds.travelerapi.model.TravelPlan;
import ua.sumdu.dds.travelerapi.repository.LocationRepository;
import ua.sumdu.dds.travelerapi.repository.TravelPlanRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TravelPlanServiceTest {

    @Mock
    private TravelPlanRepository plans;

    @Mock
    private LocationRepository locations;

    @InjectMocks
    private TravelPlanService service;

    /* ============================================
       ORIGINAL TESTS (Basic CRUD functionality)
       ============================================ */

    private CreateTravelPlanRequest validCreateRequest() {
        return new CreateTravelPlanRequest(
                "Trip",
                "Description",
                LocalDate.now(),
                LocalDate.now().plusDays(5),
                BigDecimal.valueOf(1000),
                "USD",
                true
        );
    }

    private TravelPlan existingPlan(UUID id, int version) {
        return TravelPlan.builder()
                .id(id)
                .title("Trip")
                .version(version)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(5))
                .metadata(new HashMap<>())
                .build();
    }

    @Test
    void shouldCreateTravelPlanSuccessfully() {
        CreateTravelPlanRequest req = validCreateRequest();

        when(plans.save(any(TravelPlan.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TravelPlan result = service.create(req);

        assertNotNull(result);
        assertEquals("Trip", result.getTitle());
        assertEquals(1, result.getVersion());
        verify(plans).save(any(TravelPlan.class));
    }

    @Test
    void shouldReturnPlanById() {
        UUID id = UUID.randomUUID();
        TravelPlan plan = existingPlan(id, 1);

        when(plans.findById(id)).thenReturn(Optional.of(plan));

        TravelPlan result = service.getById(id);

        assertEquals(id, result.getId());
    }

    @Test
    void shouldThrowNotFoundWhenPlanMissing() {
        UUID id = UUID.randomUUID();

        when(plans.findById(id)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.getById(id));
    }

    @Test
    void shouldUpdatePlanAndIncrementVersion() {
        UUID id = UUID.randomUUID();
        TravelPlan plan = existingPlan(id, 1);

        UpdateTravelPlanRequest req = new UpdateTravelPlanRequest(
                "New title",
                null,
                null,
                null,
                null,
                null,
                true,
                1
        );

        when(plans.findById(id)).thenReturn(Optional.of(plan));
        when(plans.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TravelPlan updated = service.update(id, req);

        assertEquals("New title", updated.getTitle());
        assertEquals(2, updated.getVersion());
    }

    @Test
    void shouldThrowVersionConflictOnUpdate() {
        UUID id = UUID.randomUUID();
        TravelPlan plan = existingPlan(id, 2);

        UpdateTravelPlanRequest req = new UpdateTravelPlanRequest(
                null, null, null, null, null, null, null, 1
        );

        when(plans.findById(id)).thenReturn(Optional.of(plan));

        assertThrows(VersionConflictException.class,
                () -> service.update(id, req));
    }

    @Test
    void shouldDeletePlan() {
        UUID id = UUID.randomUUID();

        when(plans.existsById(id)).thenReturn(true);

        service.delete(id);

        verify(plans).deleteById(id);
    }

    @Test
    void shouldThrowWhenDeletingMissingPlan() {
        UUID id = UUID.randomUUID();

        when(plans.existsById(id)).thenReturn(false);

        assertThrows(NotFoundException.class,
                () -> service.delete(id));
    }

    /* ============================================
       NEW JSONB TESTS (Metadata & Attributes)
       ============================================ */

    private TravelPlan testPlan;
    private Location testLocation;
    private UUID planId;
    private UUID locationId;

    @BeforeEach
    void setUpJsonbTests() {
        planId = UUID.randomUUID();
        locationId = UUID.randomUUID();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("preferences", Map.of("travel_style", "adventure"));
        metadata.put("tags", java.util.List.of("europe"));

        testPlan = TravelPlan.builder()
                .id(planId)
                .title("Test Plan")
                .version(1)
                .metadata(metadata)
                .build();

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("rating", 4.5);
        attributes.put("category", "museum");

        testLocation = Location.builder()
                .id(locationId)
                .travelPlan(testPlan)
                .name("Test Location")
                .version(1)
                .attributes(attributes)
                .build();
    }

    /* -------- Metadata Tests -------- */

    @Test
    void shouldUpdateMetadataWithMerge() {
        // Given: existing plan with metadata
        when(plans.findById(planId)).thenReturn(Optional.of(testPlan));
        when(plans.save(any(TravelPlan.class))).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> newMetadata = new HashMap<>();
        newMetadata.put("tags", java.util.List.of("updated"));
        MetadataUpdateRequest request = new MetadataUpdateRequest(1, newMetadata, true);

        // When: update with merge
        TravelPlan result = service.updateMetadata(planId, request);

        // Then: metadata is merged
        assertThat(result.getMetadata()).containsKey("preferences"); // preserved
        assertThat(result.getMetadata()).containsKey("tags"); // updated
        assertThat(result.getVersion()).isEqualTo(2);
        verify(plans).save(testPlan);
    }

    @Test
    void shouldUpdateMetadataWithReplace() {
        // Given: existing plan with metadata
        when(plans.findById(planId)).thenReturn(Optional.of(testPlan));
        when(plans.save(any(TravelPlan.class))).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> newMetadata = new HashMap<>();
        newMetadata.put("new_key", "new_value");
        MetadataUpdateRequest request = new MetadataUpdateRequest(1, newMetadata, false);

        // When: update with replace
        TravelPlan result = service.updateMetadata(planId, request);

        // Then: metadata is replaced
        assertThat(result.getMetadata()).doesNotContainKey("preferences"); // removed
        assertThat(result.getMetadata()).doesNotContainKey("tags"); // removed
        assertThat(result.getMetadata()).containsKey("new_key"); // new
        assertThat(result.getVersion()).isEqualTo(2);
    }

    @Test
    void shouldThrowVersionConflictOnMetadataUpdate() {
        // Given: plan with version 1
        when(plans.findById(planId)).thenReturn(Optional.of(testPlan));

        MetadataUpdateRequest request = new MetadataUpdateRequest(
                999, // wrong version
                Map.of("key", "value"),
                true
        );

        // When/Then: throws VersionConflictException
        assertThatThrownBy(() -> service.updateMetadata(planId, request))
                .isInstanceOf(VersionConflictException.class);

        verify(plans, never()).save(any());
    }

    @Test
    void shouldGetMetadata() {
        // Given: plan with metadata
        when(plans.findById(planId)).thenReturn(Optional.of(testPlan));

        // When: get metadata
        Map<String, Object> result = service.getMetadata(planId);

        // Then: returns metadata
        assertThat(result).containsKey("preferences");
        assertThat(result).containsKey("tags");
    }

    @Test
    void shouldReturnEmptyMapWhenMetadataIsNull() {
        // Given: plan with null metadata
        testPlan.setMetadata(null);
        when(plans.findById(planId)).thenReturn(Optional.of(testPlan));

        // When: get metadata
        Map<String, Object> result = service.getMetadata(planId);

        // Then: returns empty map
        assertThat(result).isEmpty();
    }

    @Test
    void shouldDeleteMetadataKey() {
        // Given: plan with metadata
        when(plans.findById(planId)).thenReturn(Optional.of(testPlan));
        when(plans.save(any(TravelPlan.class))).thenAnswer(i -> i.getArgument(0));

        // When: delete key
        TravelPlan result = service.deleteMetadataKey(planId, "tags", 1);

        // Then: key is removed
        assertThat(result.getMetadata()).containsKey("preferences");
        assertThat(result.getMetadata()).doesNotContainKey("tags");
        assertThat(result.getVersion()).isEqualTo(2);
    }

    /* -------- Attributes Tests -------- */

    @Test
    void shouldUpdateLocationAttributesWithMerge() {
        // Given: existing location with attributes
        when(plans.findById(planId)).thenReturn(Optional.of(testPlan));
        when(locations.findById(locationId)).thenReturn(Optional.of(testLocation));
        when(locations.save(any(Location.class))).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> newAttributes = new HashMap<>();
        newAttributes.put("rating", 4.9);
        AttributesUpdateRequest request = new AttributesUpdateRequest(1, newAttributes, true);

        // When: update with merge
        Location result = service.updateLocationAttributes(planId, locationId, request);

        // Then: attributes are merged
        assertThat(result.getAttributes()).containsKey("category"); // preserved
        assertThat(result.getAttributes().get("rating")).isEqualTo(4.9); // updated
        assertThat(result.getVersion()).isEqualTo(2);
        verify(locations).save(testLocation);
    }

    @Test
    void shouldUpdateLocationAttributesWithReplace() {
        // Given: existing location with attributes
        when(plans.findById(planId)).thenReturn(Optional.of(testPlan));
        when(locations.findById(locationId)).thenReturn(Optional.of(testLocation));
        when(locations.save(any(Location.class))).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> newAttributes = new HashMap<>();
        newAttributes.put("new_field", "new_value");
        AttributesUpdateRequest request = new AttributesUpdateRequest(1, newAttributes, false);

        // When: update with replace
        Location result = service.updateLocationAttributes(planId, locationId, request);

        // Then: attributes are replaced
        assertThat(result.getAttributes()).doesNotContainKey("rating"); // removed
        assertThat(result.getAttributes()).doesNotContainKey("category"); // removed
        assertThat(result.getAttributes()).containsKey("new_field"); // new
        assertThat(result.getVersion()).isEqualTo(2);
    }

    @Test
    void shouldThrowVersionConflictOnAttributesUpdate() {
        // Given: location with version 1
        when(plans.findById(planId)).thenReturn(Optional.of(testPlan));
        when(locations.findById(locationId)).thenReturn(Optional.of(testLocation));

        AttributesUpdateRequest request = new AttributesUpdateRequest(
                999, // wrong version
                Map.of("key", "value"),
                true
        );

        // When/Then: throws VersionConflictException
        assertThatThrownBy(() -> service.updateLocationAttributes(planId, locationId, request))
                .isInstanceOf(VersionConflictException.class);

        verify(locations, never()).save(any());
    }

    @Test
    void shouldThrowNotFoundWhenLocationNotInPlan() {
        // Given: location belongs to different plan
        TravelPlan otherPlan = TravelPlan.builder()
                .id(UUID.randomUUID())
                .title("Other Plan")
                .version(1)
                .metadata(new HashMap<>())
                .build();
        testLocation.setTravelPlan(otherPlan);

        when(plans.findById(planId)).thenReturn(Optional.of(testPlan));
        when(locations.findById(locationId)).thenReturn(Optional.of(testLocation));

        AttributesUpdateRequest request = new AttributesUpdateRequest(1, Map.of("key", "value"), true);

        // When/Then: throws NotFoundException
        assertThatThrownBy(() -> service.updateLocationAttributes(planId, locationId, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Location not found in travel plan");
    }

    @Test
    void shouldGetLocationAttributes() {
        // Given: location with attributes
        when(plans.findById(planId)).thenReturn(Optional.of(testPlan));
        when(locations.findById(locationId)).thenReturn(Optional.of(testLocation));

        // When: get attributes
        Map<String, Object> result = service.getLocationAttributes(planId, locationId);

        // Then: returns attributes
        assertThat(result).containsKey("rating");
        assertThat(result).containsKey("category");
    }

    @Test
    void shouldReturnEmptyMapWhenAttributesIsNull() {
        // Given: location with null attributes
        testLocation.setAttributes(null);
        when(plans.findById(planId)).thenReturn(Optional.of(testPlan));
        when(locations.findById(locationId)).thenReturn(Optional.of(testLocation));

        // When: get attributes
        Map<String, Object> result = service.getLocationAttributes(planId, locationId);

        // Then: returns empty map
        assertThat(result).isEmpty();
    }

    @Test
    void shouldDeleteLocationAttributeKey() {
        // Given: location with attributes
        when(plans.findById(planId)).thenReturn(Optional.of(testPlan));
        when(locations.findById(locationId)).thenReturn(Optional.of(testLocation));
        when(locations.save(any(Location.class))).thenAnswer(i -> i.getArgument(0));

        // When: delete key
        Location result = service.deleteLocationAttributeKey(planId, locationId, "category", 1);

        // Then: key is removed
        assertThat(result.getAttributes()).containsKey("rating");
        assertThat(result.getAttributes()).doesNotContainKey("category");
        assertThat(result.getVersion()).isEqualTo(2);
        assertThat(testPlan.getVersion()).isEqualTo(2); // parent version also incremented
    }
}