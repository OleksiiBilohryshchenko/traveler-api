package ua.sumdu.dds.travelerapi.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ua.sumdu.dds.travelerapi.dto.CreateTravelPlanRequest;
import ua.sumdu.dds.travelerapi.dto.UpdateTravelPlanRequest;
import ua.sumdu.dds.travelerapi.model.TravelPlan;
import ua.sumdu.dds.travelerapi.service.TravelPlanService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/travel-plans")
@RequiredArgsConstructor
public class TravelPlanController {

    private final TravelPlanService svc;

    @GetMapping

    public List<TravelPlan> list() {
        return svc.listAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TravelPlan create(@Valid @RequestBody CreateTravelPlanRequest req) {
        return svc.create(req);
    }

    @GetMapping("/{id}")
    public TravelPlan get(@PathVariable UUID id) {
        return svc.getById(id);
    }

    @PutMapping("/{id}")
    public TravelPlan update(@PathVariable UUID id,
                             @Valid @RequestBody UpdateTravelPlanRequest req) {
        return svc.update(id, req);
    }


    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        svc.delete(id);
    }
}
