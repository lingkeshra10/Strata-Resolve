package com.example.property.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a managed property (building complex) in the platform.
 * A Property is the top-level tenant entity — it does not extend TenantAwareEntity
 * because it IS the tenant boundary itself.
 */
@Entity
@Table(name = "property")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Property {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 500)
    private String address;

    @Column(nullable = false, length = 100)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PropertyStatus status = PropertyStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Returns true if this property is currently active and accepting new tickets.
     */
    public boolean isActive() {
        return this.status == PropertyStatus.ACTIVE;
    }

    /**
     * Deactivates this property, preventing new ticket submissions while preserving existing data.
     */
    public void deactivate() {
        this.status = PropertyStatus.INACTIVE;
    }

    /**
     * Reactivates this property, allowing new ticket submissions.
     */
    public void activate() {
        this.status = PropertyStatus.ACTIVE;
    }
}
