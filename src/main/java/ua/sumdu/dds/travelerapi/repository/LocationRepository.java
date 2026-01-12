package ua.sumdu.dds.travelerapi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ua.sumdu.dds.travelerapi.model.Location;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface LocationRepository extends JpaRepository<Location, UUID> {

    List<Location> findByTravelPlan_IdOrderByVisitOrderAsc(UUID travelPlanId);

    @Query(value = """
            SELECT * FROM locations
            WHERE attributes->>'category' = :category
            ORDER BY visit_order ASC
            """, nativeQuery = true)
    List<Location> findByCategory(@Param("category") String category);

    @Query(value = """
            SELECT * FROM locations
            WHERE CAST(attributes->>'rating' AS numeric) >= :minRating
            ORDER BY CAST(attributes->>'rating' AS numeric) DESC
            """, nativeQuery = true)
    List<Location> findByMinRating(@Param("minRating") BigDecimal minRating);

    @Query(value = """
            SELECT * FROM locations
            WHERE attributes->'accessibility' @> jsonb_build_array(:feature)
            ORDER BY visit_order ASC
            """, nativeQuery = true)
    List<Location> findByAccessibilityFeature(@Param("feature") String feature);

    @Query(value = """
            SELECT * FROM locations
            WHERE travel_plan_id = :planId
            AND attributes->>'category' = :category
            ORDER BY visit_order ASC
            """, nativeQuery = true)
    List<Location> findByTravelPlanAndCategory(
            @Param("planId") UUID planId,
            @Param("category") String category
    );

    @Query(value = """
            SELECT * FROM locations
            WHERE travel_plan_id = :planId
            AND jsonb_exists(attributes, 'rating')
            AND CAST(attributes->>'rating' AS numeric) >= :minRating
            ORDER BY CAST(attributes->>'rating' AS numeric) DESC, visit_order ASC
            """, nativeQuery = true)
    List<Location> findTopRatedInPlan(
            @Param("planId") UUID planId,
            @Param("minRating") BigDecimal minRating
    );
}