package ua.sumdu.dds.travelerapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
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
    @Builder.Default
    private BigDecimal budget = BigDecimal.ZERO;

    @Pattern(regexp = "^[A-Z]{3}$")
    @Builder.Default
    private String currency = "USD";

    @Column(name = "is_public")
    @JsonProperty("is_public")
    private boolean isPublic;


    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "travelPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("visitOrder ASC")
    private List<Location> locations;

    @PrePersist
    public void prePersist() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
        if (version == null) version = 1;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
