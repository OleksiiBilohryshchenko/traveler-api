package ua.sumdu.dds.travelerapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateTravelPlanRequest(
        @Size(max = 200) String title,
        @Size(max = 1000) String description,
        @JsonProperty("start_date") LocalDate startDate,
        @JsonProperty("end_date") LocalDate endDate,
        @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal budget,
        @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @JsonProperty("is_public") Boolean isPublic,
        @NotNull(message = "version is required")
        @Min(value = 1, message = "version must be >= 1")
        Integer version

) {}
