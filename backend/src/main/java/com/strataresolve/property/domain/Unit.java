package com.strataresolve.property.domain;

import com.strataresolve.shared.tenant.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing an individual unit (apartment, office, parking space) within a block.
 * Unit numbers must be unique within a single block.
 */
@Entity
@Table(name = "unit", uniqueConstraints = {
        @UniqueConstraint(name = "uq_unit_number_per_block", columnNames = {"block_id", "unit_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Unit extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "block_id", nullable = false)
    private UUID blockId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "block_id", insertable = false, updatable = false)
    private Block block;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", insertable = false, updatable = false)
    private Property property;

    @Column(name = "unit_number", nullable = false, length = 50)
    private String unitNumber;

    @Column
    private Integer floor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UnitType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "occupancy_status", nullable = false, length = 20)
    @Builder.Default
    private OccupancyStatus occupancyStatus = OccupancyStatus.VACANT;

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
}
