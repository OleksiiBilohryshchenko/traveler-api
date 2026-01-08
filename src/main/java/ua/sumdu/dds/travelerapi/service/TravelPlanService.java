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
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

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
}
