package ua.sumdu.dds.travelerapi.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
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

    /**
     * Flexible JSONB attributes storage.
     *
     * Example structure:
     * {
     *   "rating": 4.5,
     *   "category": "museum",
     *   "contact": {
     *     "phone": "+33123456789",
     *     "website": "https://example.com",
     *     "email": "info@example.com"
     *   },
     *   "business_hours": {
     *     "monday": "09:00-18:00",
     *     "tuesday": "09:00-18:00",
     *     "wednesday": "closed"
     *   },
     *   "accessibility": ["wheelchair", "elevator", "audio_guide"],
     *   "tags": ["historical", "art", "architecture"]
     * }
     */
    @Type(JsonBinaryType.class)
    @Column(name = "attributes", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> attributes = new HashMap<>();

    @Version
    private Integer version;

    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = OffsetDateTime.now();
        if (version == null) version = 1;
        if (attributes == null) {
            attributes = new HashMap<>();
        }
    }

    @JsonProperty("travel_plan_id")
    public UUID getTravelPlanId() {
        return travelPlan.getId();
    }
}