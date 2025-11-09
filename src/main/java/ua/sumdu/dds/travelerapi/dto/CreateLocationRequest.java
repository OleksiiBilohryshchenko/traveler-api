package ua.sumdu.dds.travelerapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CreateLocationRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 1000) String address,

        @DecimalMin("-90.0") @DecimalMax("90.0")
        BigDecimal latitude,

        @DecimalMin("-180.0") @DecimalMax("180.0")
        BigDecimal longitude,

        @JsonProperty("arrival_date")
        OffsetDateTime arrivalDate,

        @JsonProperty("departure_date")
        OffsetDateTime departureDate,

        @DecimalMin("0.00")
        @Digits(integer = 12, fraction = 2)
        BigDecimal budget,

        @Size(max = 1000)
        String notes
) {

    @AssertTrue(message = "departure_date must be after or equal to arrival_date")
    public boolean isDatesValid() {
        return arrivalDate == null || departureDate == null || !departureDate.isBefore(arrivalDate);
    }
}
