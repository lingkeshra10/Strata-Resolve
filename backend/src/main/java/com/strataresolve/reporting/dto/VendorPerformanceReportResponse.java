package com.strataresolve.reporting.dto;

import java.util.List;
import java.util.UUID;

/**
 * Response DTO for a vendor performance report.
 * Shows resolution times, SLA compliance, and ticket volumes per vendor.
 *
 * <p>Validates: Requirements 15.3
 */
public record VendorPerformanceReportResponse(
        UUID propertyId,
        int totalVendors,
        List<VendorMetrics> vendors
) {

    /**
     * Performance metrics for a single vendor.
     */
    public record VendorMetrics(
            UUID vendorId,
            String vendorName,
            int totalWorkOrders,
            int completedWorkOrders,
            double averageResolutionTimeHours,
            double slaCompliancePercent,
            int slaCompliant,
            int slaBreached
    ) {}
}
