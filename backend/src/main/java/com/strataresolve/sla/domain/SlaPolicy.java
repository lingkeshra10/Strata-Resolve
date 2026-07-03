package com.strataresolve.sla.domain;

import com.strataresolve.shared.tenant.TenantAwareEntity;
import com.strataresolve.ticket.domain.Category;
import com.strataresolve.ticket.domain.Priority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing an SLA (Service Level Agreement) policy.
 * Defines target acknowledgement and resolution times for a property/category/priority combination.
 *
 * <p>When {@code category} and {@code priority} are null and {@code isDefault} is true,
 * this policy serves as the fallback default for the property.
 * Specific policies (with category and/or priority set) take precedence over the default.
 */
@Entity
@Table(name = "sla_policy")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlaPolicy extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Priority priority;

    @Column(name = "acknowledgement_hours", nullable = false)
    private Integer acknowledgementHours;

    @Column(name = "resolution_hours", nullable = false)
    private Integer resolutionHours;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

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
