package ua.sumdu.dds.travelerapi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ua.sumdu.dds.travelerapi.model.TravelPlan;

import java.util.UUID;

public interface TravelPlanRepository extends JpaRepository<TravelPlan, UUID> {}
