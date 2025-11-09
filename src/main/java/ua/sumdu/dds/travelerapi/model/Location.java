package ua.sumdu.dds.travelerapi.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "locations")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Location {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "travel_plan_id", nullable = false)
    @JsonIgnore
    private TravelPlan travelPlan;

    @NotBlank
    @Size(max = 200)
    private String name;

    @Size(max = 1000)
    private String address;

    @DecimalMin("-90.0") @DecimalMax("90.0")
    private BigDecimal latitude;

    @DecimalMin("-180.0") @DecimalMax("180.0")
    private BigDecimal longitude;

    @JsonProperty("visit_order")
    private Integer visitOrder;

    @JsonProperty("arrival_date")
    private OffsetDateTime arrivalDate;

    @JsonProperty("departure_date")
    private OffsetDateTime departureDate;

    @DecimalMin("0.00")
    @Digits(integer = 12, fraction = 2)
    @Builder.Default
    private BigDecimal budget = BigDecimal.ZERO;

    private String notes;

    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = OffsetDateTime.now();
    }

    @JsonProperty("travel_plan_id")
    public UUID getTravelPlanId() {
        return travelPlan.getId();
    }

}
