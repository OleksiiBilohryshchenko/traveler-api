package ua.sumdu.dds.travelerapi.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ua.sumdu.dds.travelerapi.dto.CreateLocationRequest;
import ua.sumdu.dds.travelerapi.dto.UpdateLocationRequest;
import ua.sumdu.dds.travelerapi.model.Location;
import ua.sumdu.dds.travelerapi.service.TravelPlanService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LocationController {

    private final TravelPlanService svc;

    @GetMapping("/travel-plans/{planId}/locations")
    public List<Location> list(@PathVariable UUID planId) {
        return svc.listLocations(planId);
    }

    @PostMapping("/travel-plans/{planId}/locations")
    @ResponseStatus(HttpStatus.CREATED)
    public Location add(@PathVariable UUID planId,
                        @Valid @RequestBody CreateLocationRequest req) {
        return svc.addLocation(planId, req);
    }

    @PutMapping("/locations/{id}")
    public Location update(@PathVariable UUID id,
                           @Valid @RequestBody UpdateLocationRequest req) {
        return svc.updateLocation(id, req);
    }

    @DeleteMapping("/locations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        svc.deleteLocation(id);
    }
}
