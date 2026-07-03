package com.strataresolve.shared.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TenantContext ThreadLocal management.
 */
class TenantContextTest {

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void getCurrentPropertyId_returnsNull_whenNotSet() {
        assertThat(TenantContext.getCurrentPropertyId()).isNull();
    }

    @Test
    void setCurrentPropertyId_storesValue() {
        UUID propertyId = UUID.randomUUID();
        TenantContext.setCurrentPropertyId(propertyId);

        assertThat(TenantContext.getCurrentPropertyId()).isEqualTo(propertyId);
    }

    @Test
    void clear_removesStoredValue() {
        UUID propertyId = UUID.randomUUID();
        TenantContext.setCurrentPropertyId(propertyId);

        TenantContext.clear();

        assertThat(TenantContext.getCurrentPropertyId()).isNull();
    }

    @Test
    void isSet_returnsFalse_whenNotSet() {
        assertThat(TenantContext.isSet()).isFalse();
    }

    @Test
    void isSet_returnsTrue_whenSet() {
        TenantContext.setCurrentPropertyId(UUID.randomUUID());

        assertThat(TenantContext.isSet()).isTrue();
    }

    @Test
    void isSet_returnsFalse_afterClear() {
        TenantContext.setCurrentPropertyId(UUID.randomUUID());
        TenantContext.clear();

        assertThat(TenantContext.isSet()).isFalse();
    }
}
