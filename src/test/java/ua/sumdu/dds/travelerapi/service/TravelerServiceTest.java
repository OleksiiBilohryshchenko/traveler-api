package ua.sumdu.dds.travelerapi.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ua.sumdu.dds.travelerapi.dto.CreateTravelPlanRequest;
import ua.sumdu.dds.travelerapi.dto.UpdateTravelPlanRequest;
import ua.sumdu.dds.travelerapi.exception.NotFoundException;
import ua.sumdu.dds.travelerapi.exception.VersionConflictException;
import ua.sumdu.dds.travelerapi.model.TravelPlan;
import ua.sumdu.dds.travelerapi.repository.LocationRepository;
import ua.sumdu.dds.travelerapi.repository.TravelPlanRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TravelPlanServiceTest {

    @Mock
    private TravelPlanRepository plans;

    @Mock
    private LocationRepository locations;

    @InjectMocks
    private TravelPlanService service;

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



}
