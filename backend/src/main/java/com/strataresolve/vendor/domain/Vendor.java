package com.strataresolve.vendor.domain;

import com.strataresolve.shared.tenant.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing an external vendor (maintenance company) associated with a property.
 * Vendors perform assigned work orders and can have multiple Vendor_Technician users
 * linked via membership records.
 */
@Entity
@Table(name = "vendor")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vendor extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(name = "contact_phone", length = 50)
    private String contactPhone;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    /**
     * Deactivates this vendor, preventing new work order assignments.
     */
    public void deactivate() {
        this.isActive = false;
    }

    /**
     * Reactivates this vendor, allowing new work order assignments.
     */
    public void activate() {
        this.isActive = true;
    }
}
