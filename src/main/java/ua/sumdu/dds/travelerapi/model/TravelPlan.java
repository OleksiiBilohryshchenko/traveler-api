package ua.sumdu.dds.travelerapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "travel_plans")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class TravelPlan {

    @Id
    @GeneratedValue
    private UUID id;

    @Version
    private Integer version;

    @NotBlank
    @Size(max = 200)
    private String title;

    @Size(max = 1000)
    private String description;

    private LocalDate startDate;

    private LocalDate endDate;

    @DecimalMin("0.00")
    @Digits(integer = 12, fraction = 2)
    private BigDecimal budget = BigDecimal.ZERO;

    @Pattern(regexp = "^[A-Z]{3}$")
    @Builder.Default
    private String currency = "USD";

    @Column(name = "is_public")
    @JsonProperty("is_public")
    private boolean isPublic;

    /**
     * Flexible JSONB metadata storage.
     *
     * Example structure:
     * {
     *   "preferences": {
     *     "budget_category": "moderate",
     *     "travel_style": "adventure",
     *     "pace": "relaxed"
     *   },
     *   "participants": [
     *     {"name": "John", "age": 30, "role": "organizer"}
     *   ],
     *   "tags": ["family", "summer", "europe"],
     *   "custom_fields": {
     *     "any_user_defined_key": "any_value"
     *   }
     * }
     */
    @Type(JsonBinaryType.class)
    @Column(name = "metadata", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "travelPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("visitOrder ASC")
    private List<Location> locations;

    @PrePersist
    public void prePersist() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
        if (metadata == null) {
            metadata = new HashMap<>();
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}