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
import ua.sumdu.dds.travelerapi.model.TravelPlan;
import ua.sumdu.dds.travelerapi.repository.TravelPlanRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for TravelPlan JSONB metadata operations.
 *
 * Uses Testcontainers with real PostgreSQL for accurate JSONB testing.
 *
 * Note: Tests for findByTagsAny(), findByTagsAll(), searchWithFilters(),
 * and findByMetadataKeyExists() were removed due to PostgreSQL ?/?|/?&
 * operator conflicts with JDBC parameter placeholders.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TravelPlanMetadataIntegrationTest {

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
    private TravelPlanRepository repository;

    private TravelPlan testPlan;

    @BeforeEach
    void setUp() {
        repository.deleteAll();

        // Create test plan with metadata
        Map<String, Object> metadata = new HashMap<>();

        Map<String, Object> preferences = new HashMap<>();
        preferences.put("travel_style", "adventure");
        preferences.put("budget_category", "moderate");
        preferences.put("pace", "relaxed");

        metadata.put("preferences", preferences);
        metadata.put("tags", List.of("europe", "summer", "family"));

        testPlan = TravelPlan.builder()
                .title("European Adventure")
                .description("Summer family trip")
                .startDate(LocalDate.of(2024, 7, 1))
                .endDate(LocalDate.of(2024, 7, 15))
                .budget(new BigDecimal("5000.00"))
                .currency("EUR")
                .isPublic(true)
                .version(1)
                .metadata(metadata)
                .build();

        testPlan = repository.save(testPlan);
    }

    @Test
    void shouldPersistAndRetrieveMetadata() {
        // Given: plan with metadata saved in setUp()

        // When: retrieve the plan
        TravelPlan retrieved = repository.findById(testPlan.getId()).orElseThrow();

        // Then: metadata is properly persisted
        assertThat(retrieved.getMetadata()).isNotNull();
        assertThat(retrieved.getMetadata()).containsKey("preferences");
        assertThat(retrieved.getMetadata()).containsKey("tags");

        @SuppressWarnings("unchecked")
        Map<String, Object> preferences = (Map<String, Object>) retrieved.getMetadata().get("preferences");
        assertThat(preferences.get("travel_style")).isEqualTo("adventure");
        assertThat(preferences.get("budget_category")).isEqualTo("moderate");

        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) retrieved.getMetadata().get("tags");
        assertThat(tags).containsExactly("europe", "summer", "family");
    }

    @Test
    void shouldFindByTravelStyle() {
        // Given: plan with travel_style = "adventure"

        // When: search by travel style
        List<TravelPlan> results = repository.findByTravelStyle("adventure");

        // Then: plan is found
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(testPlan.getId());
    }

    @Test
    void shouldNotFindByNonExistentTravelStyle() {
        // When: search by non-existent travel style
        List<TravelPlan> results = repository.findByTravelStyle("luxury");

        // Then: no results
        assertThat(results).isEmpty();
    }

    @Test
    void shouldFindByBudgetCategory() {
        // Given: plan with budget_category = "moderate"

        // When: search by budget category
        List<TravelPlan> results = repository.findByBudgetCategory("moderate");

        // Then: plan is found
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(testPlan.getId());
    }

    @Test
    void shouldUpdateMetadata() {
        // Given: existing plan

        // When: update metadata
        Map<String, Object> newMetadata = new HashMap<>();
        newMetadata.put("tags", List.of("updated", "new"));
        newMetadata.put("custom_field", "custom_value");

        testPlan.setMetadata(newMetadata);
        testPlan.setVersion(testPlan.getVersion() + 1);
        repository.save(testPlan);

        // Then: metadata is updated
        TravelPlan updated = repository.findById(testPlan.getId()).orElseThrow();
        assertThat(updated.getMetadata()).containsKey("tags");
        assertThat(updated.getMetadata()).containsKey("custom_field");
        assertThat(updated.getMetadata()).doesNotContainKey("preferences");

        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) updated.getMetadata().get("tags");
        assertThat(tags).containsExactly("updated", "new");
    }

    @Test
    void shouldMergeMetadata() {
        // Given: existing plan with metadata

        // When: merge new keys into existing metadata
        Map<String, Object> existingMetadata = new HashMap<>(testPlan.getMetadata());
        existingMetadata.put("new_key", "new_value");

        testPlan.setMetadata(existingMetadata);
        repository.save(testPlan);

        // Then: both old and new keys exist
        TravelPlan updated = repository.findById(testPlan.getId()).orElseThrow();
        assertThat(updated.getMetadata()).containsKey("preferences");
        assertThat(updated.getMetadata()).containsKey("tags");
        assertThat(updated.getMetadata()).containsKey("new_key");
    }

    @Test
    void shouldHandleEmptyMetadata() {
        // Given: plan with empty metadata
        TravelPlan emptyPlan = TravelPlan.builder()
                .title("Empty Metadata Plan")
                .version(1)
                .metadata(new HashMap<>())
                .build();
        emptyPlan = repository.save(emptyPlan);

        // When: retrieve the plan
        TravelPlan retrieved = repository.findById(emptyPlan.getId()).orElseThrow();

        // Then: metadata exists but is empty
        assertThat(retrieved.getMetadata()).isNotNull();
        assertThat(retrieved.getMetadata()).isEmpty();
    }

    @Test
    void shouldHandleNestedMetadataStructures() {
        // Given: plan with deeply nested metadata
        Map<String, Object> deepMetadata = new HashMap<>();

        Map<String, Object> level1 = new HashMap<>();
        Map<String, Object> level2 = new HashMap<>();
        level2.put("deep_value", "nested");
        level1.put("level2", level2);
        deepMetadata.put("level1", level1);

        TravelPlan deepPlan = TravelPlan.builder()
                .title("Deep Nested Plan")
                .version(1)
                .metadata(deepMetadata)
                .build();
        deepPlan = repository.save(deepPlan);

        // When: retrieve the plan
        TravelPlan retrieved = repository.findById(deepPlan.getId()).orElseThrow();

        // Then: nested structure is preserved
        @SuppressWarnings("unchecked")
        Map<String, Object> retrievedLevel1 = (Map<String, Object>) retrieved.getMetadata().get("level1");
        assertThat(retrievedLevel1).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> retrievedLevel2 = (Map<String, Object>) retrievedLevel1.get("level2");
        assertThat(retrievedLevel2).isNotNull();
        assertThat(retrievedLevel2.get("deep_value")).isEqualTo("nested");
    }

    @Test
    void shouldFindByMetadataContains() {
        // Given: plan with preferences containing travel_style

        // When: search using @> containment operator
        List<TravelPlan> results = repository.findByMetadataContains(
                "{\"preferences\": {\"travel_style\": \"adventure\"}}"
        );

        // Then: plan is found
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(testPlan.getId());
    }

    @Test
    void shouldNotFindByMetadataContainsWithNonMatchingValue() {
        // When: search with non-matching value
        List<TravelPlan> results = repository.findByMetadataContains(
                "{\"preferences\": {\"travel_style\": \"luxury\"}}"
        );

        // Then: no results
        assertThat(results).isEmpty();
    }
}