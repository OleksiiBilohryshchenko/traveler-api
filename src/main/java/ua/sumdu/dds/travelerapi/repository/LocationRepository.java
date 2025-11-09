package ua.sumdu.dds.travelerapi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ua.sumdu.dds.travelerapi.model.Location;

import java.util.List;
import java.util.UUID;

public interface LocationRepository extends JpaRepository<Location, UUID> {

    List<Location> findByTravelPlan_IdOrderByVisitOrderAsc(UUID travelPlanId);

}
