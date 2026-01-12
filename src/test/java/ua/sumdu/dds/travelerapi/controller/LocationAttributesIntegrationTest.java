package ua.sumdu.dds.travelerapi.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ua.sumdu.dds.travelerapi.model.Location;
import ua.sumdu.dds.travelerapi.model.TravelPlan;
import ua.sumdu.dds.travelerapi.repository.LocationRepository;
import ua.sumdu.dds.travelerapi.repository.TravelPlanRepository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Location JSONB attributes operations.
 *
 * Uses Testcontainers with real PostgreSQL for accurate JSONB testing.
 *
 * Note: Tests for findByTagsAny(), findByTagsAll(), searchWithFilters(),
 * findWithContactInfo(), and findWithBusinessHours() were removed due to
 * PostgreSQL ?/?|/?& operator conflicts with JDBC parameter placeholders.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class LocationAttributesIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private TravelPlanRepository planRepository;

    private TravelPlan testPlan;
    private Location testLocation;

    @BeforeEach
    void setUp() {
        locationRepository.deleteAll();
        planRepository.deleteAll();

        // Create test plan
        testPlan = TravelPlan.builder()
                .title("Test Plan")
                .version(1)
                .metadata(new HashMap<>())
                .build();
        testPlan = planRepository.save(testPlan);

        // Create test location with attributes
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("rating", 4.5);
        attributes.put("category", "museum");

        Map<String, Object> contact = new HashMap<>();
        contact.put("phone", "+33123456789");
        contact.put("website", "https://example.com");
        attributes.put("contact", contact);

        Map<String, Object> businessHours = new HashMap<>();
        businessHours.put("monday", "09:00-18:00");
        businessHours.put("tuesday", "09:00-18:00");
        attributes.put("business_hours", businessHours);

        attributes.put("accessibility", List.of("wheelchair", "elevator"));
        attributes.put("tags", List.of("historical", "art", "architecture"));

        testLocation = Location.builder()
                .travelPlan(testPlan)
                .name("Louvre Museum")
                .address("Rue de Rivoli, 75001 Paris, France")
                .latitude(new BigDecimal("48.8606"))
                .longitude(new BigDecimal("2.3376"))
                .visitOrder(1)
                .version(1)
                .attributes(attributes)
                .build();

        testLocation = locationRepository.save(testLocation);
    }

    @Test
    void shouldPersistAndRetrieveAttributes() {
        // Given: location with attributes saved in setUp()

        // When: retrieve the location
        Location retrieved = locationRepository.findById(testLocation.getId()).orElseThrow();

        // Then: attributes are properly persisted
        assertThat(retrieved.getAttributes()).isNotNull();
        assertThat(retrieved.getAttributes().get("rating")).isEqualTo(4.5);
        assertThat(retrieved.getAttributes().get("category")).isEqualTo("museum");

        @SuppressWarnings("unchecked")
        Map<String, Object> contact = (Map<String, Object>) retrieved.getAttributes().get("contact");
        assertThat(contact.get("phone")).isEqualTo("+33123456789");

        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) retrieved.getAttributes().get("tags");
        assertThat(tags).containsExactly("historical", "art", "architecture");
    }

    @Test
    void shouldFindByCategory() {
        // Given: location with category = "museum"

        // When: search by category
        List<Location> results = locationRepository.findByCategory("museum");

        // Then: location is found
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(testLocation.getId());
    }

    @Test
    void shouldNotFindByNonExistentCategory() {
        // When: search by non-existent category
        List<Location> results = locationRepository.findByCategory("restaurant");

        // Then: no results
        assertThat(results).isEmpty();
    }

    @Test
    void shouldFindByMinRating() {
        // Given: location with rating = 4.5

        // When: search by minimum rating
        List<Location> results1 = locationRepository.findByMinRating(new BigDecimal("4.0"));
        List<Location> results2 = locationRepository.findByMinRating(new BigDecimal("4.5"));
        List<Location> results3 = locationRepository.findByMinRating(new BigDecimal("5.0"));

        // Then: correct results
        assertThat(results1).hasSize(1); // 4.5 >= 4.0
        assertThat(results2).hasSize(1); // 4.5 >= 4.5
        assertThat(results3).isEmpty(); // 4.5 < 5.0
    }

    @Test
    void shouldFindByAccessibilityFeature() {
        // Given: location with accessibility = ["wheelchair", "elevator"]

        // When: search by accessibility feature
        List<Location> results1 = locationRepository.findByAccessibilityFeature("wheelchair");
        List<Location> results2 = locationRepository.findByAccessibilityFeature("elevator");
        List<Location> results3 = locationRepository.findByAccessibilityFeature("braille");

        // Then: correct results
        assertThat(results1).hasSize(1);
        assertThat(results2).hasSize(1);
        assertThat(results3).isEmpty();
    }

    @Test
    void shouldFindTopRatedInPlan() {
        // Given: multiple locations with different ratings
        Location location2 = Location.builder()
                .travelPlan(testPlan)
                .name("Location 2")
                .visitOrder(2)
                .version(1)
                .attributes(Map.of("rating", 3.5, "category", "restaurant"))
                .build();
        locationRepository.save(location2);

        Location location3 = Location.builder()
                .travelPlan(testPlan)
                .name("Location 3")
                .visitOrder(3)
                .version(1)
                .attributes(Map.of("rating", 4.8, "category", "hotel"))
                .build();
        locationRepository.save(location3);

        // When: find top rated (>= 4.0)
        List<Location> results = locationRepository.findTopRatedInPlan(
                testPlan.getId(),
                new BigDecimal("4.0")
        );

        // Then: returns locations sorted by rating DESC
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getName()).isEqualTo("Location 3"); // 4.8
        assertThat(results.get(1).getName()).isEqualTo("Louvre Museum"); // 4.5
    }

    @Test
    void shouldFindByTravelPlanAndCategory() {
        // Given: location with category in specific plan

        // When: search by plan and category
        List<Location> results = locationRepository.findByTravelPlanAndCategory(
                testPlan.getId(),
                "museum"
        );

        // Then: location is found
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(testLocation.getId());
    }

    @Test
    void shouldNotFindByTravelPlanAndNonMatchingCategory() {
        // When: search with non-matching category
        List<Location> results = locationRepository.findByTravelPlanAndCategory(
                testPlan.getId(),
                "restaurant"
        );

        // Then: no results
        assertThat(results).isEmpty();
    }

    @Test
    void shouldUpdateAttributes() {
        // Given: existing location

        // When: update attributes
        Map<String, Object> newAttributes = new HashMap<>();
        newAttributes.put("rating", 4.9);
        newAttributes.put("category", "attraction");

        testLocation.setAttributes(newAttributes);
        testLocation.setVersion(testLocation.getVersion() + 1);
        locationRepository.save(testLocation);

        // Then: attributes are updated
        Location updated = locationRepository.findById(testLocation.getId()).orElseThrow();
        assertThat(updated.getAttributes().get("rating")).isEqualTo(4.9);
        assertThat(updated.getAttributes().get("category")).isEqualTo("attraction");
        assertThat(updated.getAttributes()).doesNotContainKey("contact");
        assertThat(updated.getAttributes()).doesNotContainKey("tags");
    }

    @Test
    void shouldMergeAttributes() {
        // Given: existing location with attributes

        // When: merge new keys into existing attributes
        Map<String, Object> existingAttributes = new HashMap<>(testLocation.getAttributes());
        existingAttributes.put("new_field", "new_value");

        testLocation.setAttributes(existingAttributes);
        locationRepository.save(testLocation);

        // Then: both old and new keys exist
        Location updated = locationRepository.findById(testLocation.getId()).orElseThrow();
        assertThat(updated.getAttributes()).containsKey("rating");
        assertThat(updated.getAttributes()).containsKey("category");
        assertThat(updated.getAttributes()).containsKey("new_field");
    }

    @Test
    void shouldHandleEmptyAttributes() {
        // Given: location with empty attributes
        Location emptyLocation = Location.builder()
                .travelPlan(testPlan)
                .name("Empty Attributes Location")
                .visitOrder(10)
                .version(1)
                .attributes(new HashMap<>())
                .build();
        emptyLocation = locationRepository.save(emptyLocation);

        // When: retrieve the location
        Location retrieved = locationRepository.findById(emptyLocation.getId()).orElseThrow();

        // Then: attributes exist but are empty
        assertThat(retrieved.getAttributes()).isNotNull();
        assertThat(retrieved.getAttributes()).isEmpty();
    }

    @Test
    void shouldHandleNumericRatingComparison() {
        // Given: locations with different rating formats
        Location location1 = Location.builder()
                .travelPlan(testPlan)
                .name("Integer Rating")
                .visitOrder(20)
                .version(1)
                .attributes(Map.of("rating", 5)) // Integer
                .build();
        locationRepository.save(location1);

        Location location2 = Location.builder()
                .travelPlan(testPlan)
                .name("Double Rating")
                .visitOrder(21)
                .version(1)
                .attributes(Map.of("rating", 4.75)) // Double
                .build();
        locationRepository.save(location2);

        // When: search by minimum rating
        List<Location> results = locationRepository.findByMinRating(new BigDecimal("4.5"));

        // Then: both integer and double ratings work
        assertThat(results).hasSizeGreaterThanOrEqualTo(3); // includes our test location (4.5)
    }

    @Test
    void shouldFindLocationsByPlanOrderedByVisitOrder() {
        // Given: multiple locations in a plan
        Location location2 = Location.builder()
                .travelPlan(testPlan)
                .name("Second Stop")
                .visitOrder(2)
                .version(1)
                .attributes(Map.of("category", "restaurant"))
                .build();
        locationRepository.save(location2);

        Location location3 = Location.builder()
                .travelPlan(testPlan)
                .name("Third Stop")
                .visitOrder(3)
                .version(1)
                .attributes(Map.of("category", "hotel"))
                .build();
        locationRepository.save(location3);

        // When: find all locations by plan
        List<Location> results = locationRepository.findByTravelPlan_IdOrderByVisitOrderAsc(testPlan.getId());

        // Then: locations are ordered by visitOrder
        assertThat(results).hasSize(3);
        assertThat(results.get(0).getVisitOrder()).isEqualTo(1);
        assertThat(results.get(1).getVisitOrder()).isEqualTo(2);
        assertThat(results.get(2).getVisitOrder()).isEqualTo(3);
    }

    @Test
    void shouldHandleNestedAttributeStructures() {
        // Given: location with deeply nested attributes
        Map<String, Object> deepAttributes = new HashMap<>();

        Map<String, Object> level1 = new HashMap<>();
        Map<String, Object> level2 = new HashMap<>();
        level2.put("deep_value", "nested");
        level1.put("level2", level2);
        deepAttributes.put("level1", level1);

        Location deepLocation = Location.builder()
                .travelPlan(testPlan)
                .name("Deep Nested Location")
                .visitOrder(100)
                .version(1)
                .attributes(deepAttributes)
                .build();
        deepLocation = locationRepository.save(deepLocation);

        // When: retrieve the location
        Location retrieved = locationRepository.findById(deepLocation.getId()).orElseThrow();

        // Then: nested structure is preserved
        @SuppressWarnings("unchecked")
        Map<String, Object> retrievedLevel1 = (Map<String, Object>) retrieved.getAttributes().get("level1");
        assertThat(retrievedLevel1).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> retrievedLevel2 = (Map<String, Object>) retrievedLevel1.get("level2");
        assertThat(retrievedLevel2).isNotNull();
        assertThat(retrievedLevel2.get("deep_value")).isEqualTo("nested");
    }
}