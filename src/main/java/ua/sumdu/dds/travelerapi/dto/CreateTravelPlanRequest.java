package ua.sumdu.dds.travelerapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateTravelPlanRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 1000) String description,
        @JsonProperty("start_date") LocalDate startDate,
        @JsonProperty("end_date") LocalDate endDate,
        BigDecimal budget,
        String currency,
        @JsonProperty("is_public") Boolean isPublic
) {
    @AssertTrue(message = "end_date must be after or equal to start_date")
    public boolean isDatesValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
}
