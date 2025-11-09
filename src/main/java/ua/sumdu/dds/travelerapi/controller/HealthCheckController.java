package ua.sumdu.dds.travelerapi.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ua.sumdu.dds.travelerapi.dto.HealthCheckResponse;
import ua.sumdu.dds.travelerapi.service.HealthCheckService;

import java.time.OffsetDateTime;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class HealthCheckController {

    private final HealthCheckService healthCheckService;

    @GetMapping("/api/health")
    public HealthCheckResponse health() {

        Map<String, Object> db = healthCheckService.checkDatabase();
        String dbStatus = (String) db.get("status");

        String overallStatus = dbStatus.equals("healthy") ? "healthy" : "degraded";

        return new HealthCheckResponse(
                overallStatus,
                OffsetDateTime.now(),
                healthCheckService.getUptime(),
                db
        );
    }
}
