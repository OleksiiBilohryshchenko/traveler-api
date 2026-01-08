package ua.sumdu.dds.travelerapi.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ua.sumdu.dds.travelerapi.model.TravelPlan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(exclude = FlywayAutoConfiguration.class)
class TravelPlanRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("testdb")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TravelPlanRepository repository;

    private TravelPlan samplePlan() {
        return TravelPlan.builder()
                .title("Test Trip")
                .description("Repository test")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(2))
                .budget(BigDecimal.valueOf(1000))
                .currency("USD")
                .isPublic(true)
                .version(1)
                .build();
    }

    @Test
    void shouldSaveAndFindById() {
        TravelPlan saved = repository.save(samplePlan());

        Optional<TravelPlan> found = repository.findById(saved.getId());

        assertTrue(found.isPresent());
        assertEquals(saved.getId(), found.get().getId());
    }

    @Test
    void shouldDeleteEntity() {
        TravelPlan saved = repository.save(samplePlan());

        repository.deleteById(saved.getId());

        assertTrue(repository.findById(saved.getId()).isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenNotFound() {
        assertTrue(repository.findById(UUID.randomUUID()).isEmpty());
    }
}
