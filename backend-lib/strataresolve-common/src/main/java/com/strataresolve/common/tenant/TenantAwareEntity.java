package com.strataresolve.common.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.util.UUID;

/**
 * Base class for JPA entities that are scoped to a specific property (tenant).
 *
 * <p>Defines the Hibernate filter {@code tenantFilter} which automatically adds
 * {@code WHERE property_id = :propertyId} to all queries on entities extending this class.
 * The filter is enabled per-request by {@link TenantContextFilter}.
 *
 * <p>Entities that have a {@code property_id} column should extend this class to
 * automatically participate in multi-tenancy data isolation.
 */
@MappedSuperclass
@FilterDef(
        name = TenantContextFilter.TENANT_FILTER_NAME,
        parameters = @ParamDef(name = "propertyId", type = String.class),
        defaultCondition = "CAST(property_id AS VARCHAR) = :propertyId"
)
@Filter(name = TenantContextFilter.TENANT_FILTER_NAME)
public abstract class TenantAwareEntity {

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    protected TenantAwareEntity() {
    }

    protected TenantAwareEntity(UUID propertyId) {
        this.propertyId = propertyId;
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public void setPropertyId(UUID propertyId) {
        this.propertyId = propertyId;
    }
}
