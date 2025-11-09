
package ua.sumdu.dds.travelerapi.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record UpdateLocationRequest(
        @Size(max = 200) String name,
        @Size(max = 1000) String address,
        @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        @JsonProperty("arrival_date") @JsonFormat(shape = JsonFormat.Shape.STRING) OffsetDateTime arrivalDate,
        @JsonProperty("departure_date") @JsonFormat(shape = JsonFormat.Shape.STRING) OffsetDateTime departureDate,
        @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal budget,
        String notes
) {}
