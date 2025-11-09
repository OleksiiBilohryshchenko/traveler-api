package ua.sumdu.dds.travelerapi.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record HealthCheckResponse(
        String status,
        OffsetDateTime timestamp,
        long uptime,
        Map<String, Object> database
) {}
