package ua.sumdu.dds.travelerapi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ua.sumdu.dds.travelerapi.model.TravelPlan;

import java.util.List;
import java.util.UUID;

public interface TravelPlanRepository extends JpaRepository<TravelPlan, UUID> {

    @Query(value = """
            SELECT * FROM travel_plans
            WHERE metadata->'preferences'->>'travel_style' = :travelStyle
            """, nativeQuery = true)
    List<TravelPlan> findByTravelStyle(@Param("travelStyle") String travelStyle);

    @Query(value = """
            SELECT * FROM travel_plans
            WHERE metadata->'preferences'->>'budget_category' = :budgetCategory
            """, nativeQuery = true)
    List<TravelPlan> findByBudgetCategory(@Param("budgetCategory") String budgetCategory);

    @Query(value = """
            SELECT * FROM travel_plans
            WHERE metadata @> CAST(:metadataJson AS jsonb)
            """, nativeQuery = true)
    List<TravelPlan> findByMetadataContains(@Param("metadataJson") String metadataJson);
}