package com.strataresolve.identity.domain;

import com.strataresolve.common.tenant.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JPA entity representing a user's membership (role assignment) within a property.
 * A membership links a user to a property with a specific role, optionally linked to a unit.
 * Deactivation sets is_active to false and effective_to date, preserving historical data.
 */
@Entity
@Table(name = "membership")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Membership extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @Column(name = "unit_id")
    private UUID unitId;

    @Column(name = "vendor_id")
    private UUID vendorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private Role role;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    /**
     * Deactivates this membership by setting is_active to false and recording the effective_to date.
     * This preserves the historical record while revoking access.
     */
    public void deactivate() {
        this.isActive = false;
        this.effectiveTo = LocalDate.now();
    }

    /**
     * Links this membership to a specific unit (for resident roles).
     */
    public void linkToUnit(UUID unitId) {
        this.unitId = unitId;
    }

    /**
     * Links this membership to a specific vendor (for VENDOR_TECHNICIAN or VENDOR_ADMIN roles).
     */
    public void linkToVendor(UUID vendorId) {
        this.vendorId = vendorId;
    }
}
