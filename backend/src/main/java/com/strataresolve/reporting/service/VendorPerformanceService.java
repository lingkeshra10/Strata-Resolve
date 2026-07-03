package com.strataresolve.reporting.service;

import com.strataresolve.reporting.dto.VendorPerformanceReportResponse;
import com.strataresolve.reporting.dto.VendorPerformanceReportResponse.VendorMetrics;
import com.strataresolve.ticket.domain.SlaStatus;
import com.strataresolve.ticket.domain.Ticket;
import com.strataresolve.ticket.repository.TicketRepository;
import com.strataresolve.vendor.domain.Vendor;
import com.strataresolve.vendor.domain.WorkOrder;
import com.strataresolve.vendor.domain.WorkOrderStatus;
import com.strataresolve.vendor.repository.VendorRepository;
import com.strataresolve.vendor.repository.WorkOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for generating vendor performance reports.
 * Aggregates resolution times, SLA compliance, and ticket volumes per vendor.
 *
 * <p>Validates: Requirements 15.3
 * <p>Metrics computed per vendor:
 * <ul>
 *   <li>Average resolution time: mean time from work order creation to completion</li>
 *   <li>SLA compliance rate: percentage of associated tickets that are not SLA-breached</li>
 *   <li>Total completed work orders</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class VendorPerformanceService {

    private final VendorRepository vendorRepository;
    private final WorkOrderRepository workOrderRepository;
    private final TicketRepository ticketRepository;

    public VendorPerformanceService(VendorRepository vendorRepository,
                                     WorkOrderRepository workOrderRepository,
                                     TicketRepository ticketRepository) {
        this.vendorRepository = vendorRepository;
        this.workOrderRepository = workOrderRepository;
        this.ticketRepository = ticketRepository;
    }

    /**
     * Generates a vendor performance report for a property.
     *
     * @param propertyId the property to report on
     * @return the vendor performance report with per-vendor metrics
     */
    public VendorPerformanceReportResponse generateReport(UUID propertyId) {
        List<Vendor> vendors = vendorRepository.findByPropertyId(propertyId);
        List<WorkOrder> allWorkOrders = workOrderRepository.findByPropertyId(propertyId);

        // Group work orders by vendor
        Map<UUID, List<WorkOrder>> workOrdersByVendor = allWorkOrders.stream()
                .collect(Collectors.groupingBy(WorkOrder::getVendorId));

        // Pre-fetch all tickets for this property for SLA lookup
        List<Ticket> allTickets = ticketRepository.findByPropertyId(propertyId);
        Map<UUID, Ticket> ticketById = allTickets.stream()
                .collect(Collectors.toMap(Ticket::getId, t -> t, (a, b) -> a));

        List<VendorMetrics> vendorMetricsList = new ArrayList<>();

        for (Vendor vendor : vendors) {
            List<WorkOrder> vendorOrders = workOrdersByVendor.getOrDefault(vendor.getId(), List.of());
            VendorMetrics metrics = computeMetrics(vendor, vendorOrders, ticketById);
            vendorMetricsList.add(metrics);
        }

        return new VendorPerformanceReportResponse(
                propertyId,
                vendors.size(),
                vendorMetricsList
        );
    }

    /**
     * Computes performance metrics for a single vendor.
     */
    VendorMetrics computeMetrics(Vendor vendor, List<WorkOrder> workOrders,
                                  Map<UUID, Ticket> ticketById) {
        int totalWorkOrders = workOrders.size();
        int completedWorkOrders = 0;
        long totalResolutionMillis = 0;
        int resolutionCount = 0;
        int slaCompliant = 0;
        int slaBreached = 0;

        for (WorkOrder wo : workOrders) {
            if (wo.getStatus() == WorkOrderStatus.COMPLETED) {
                completedWorkOrders++;

                // Compute resolution time (creation to completion)
                if (wo.getCompletedAt() != null && wo.getCreatedAt() != null) {
                    Duration resolutionTime = Duration.between(wo.getCreatedAt(), wo.getCompletedAt());
                    totalResolutionMillis += resolutionTime.toMillis();
                    resolutionCount++;
                }
            }

            // Check SLA compliance on the associated ticket
            Ticket ticket = ticketById.get(wo.getTicketId());
            if (ticket != null) {
                if (isTicketSlaCompliant(ticket)) {
                    slaCompliant++;
                } else {
                    slaBreached++;
                }
            }
        }

        double averageResolutionTimeHours = 0.0;
        if (resolutionCount > 0) {
            averageResolutionTimeHours = (totalResolutionMillis / (double) resolutionCount) / 3_600_000.0;
        }

        int slaTotalAssessed = slaCompliant + slaBreached;
        double slaCompliancePercent = slaTotalAssessed == 0 ? 100.0 : (slaCompliant * 100.0) / slaTotalAssessed;

        return new VendorMetrics(
                vendor.getId(),
                vendor.getName(),
                totalWorkOrders,
                completedWorkOrders,
                averageResolutionTimeHours,
                slaCompliancePercent,
                slaCompliant,
                slaBreached
        );
    }

    /**
     * Determines if a ticket's SLA is compliant (not breached in any form).
     */
    boolean isTicketSlaCompliant(Ticket ticket) {
        return ticket.getSlaStatus() == SlaStatus.ON_TRACK;
    }
}
