package com.strataresolve.reporting.service;

import com.strataresolve.reporting.dto.VendorPerformanceReportResponse;
import com.strataresolve.reporting.dto.VendorPerformanceReportResponse.VendorMetrics;
import com.strataresolve.ticket.domain.Category;
import com.strataresolve.ticket.domain.Priority;
import com.strataresolve.ticket.domain.SlaStatus;
import com.strataresolve.ticket.domain.Ticket;
import com.strataresolve.ticket.domain.TicketStatus;
import com.strataresolve.ticket.repository.TicketRepository;
import com.strataresolve.vendor.domain.Vendor;
import com.strataresolve.vendor.domain.WorkOrder;
import com.strataresolve.vendor.domain.WorkOrderStatus;
import com.strataresolve.vendor.repository.VendorRepository;
import com.strataresolve.vendor.repository.WorkOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("VendorPerformanceService")
class VendorPerformanceServiceTest {

    @Mock
    private VendorRepository vendorRepository;

    @Mock
    private WorkOrderRepository workOrderRepository;

    @Mock
    private TicketRepository ticketRepository;

    private VendorPerformanceService vendorPerformanceService;

    private UUID propertyId;
    private Instant now;

    @BeforeEach
    void setUp() {
        vendorPerformanceService = new VendorPerformanceService(vendorRepository, workOrderRepository, ticketRepository);
        propertyId = UUID.randomUUID();
        now = Instant.now();
    }

    private Vendor createVendor(UUID vendorId, String name) {
        Vendor vendor = Vendor.builder()
                .id(vendorId)
                .name(name)
                .contactEmail(name.toLowerCase() + "@example.com")
                .isActive(true)
                .createdAt(now.minus(30, ChronoUnit.DAYS))
                .build();
        vendor.setPropertyId(propertyId);
        return vendor;
    }

    private WorkOrder createWorkOrder(UUID vendorId, UUID ticketId, WorkOrderStatus status,
                                       Instant createdAt, Instant completedAt) {
        WorkOrder wo = WorkOrder.builder()
                .id(UUID.randomUUID())
                .vendorId(vendorId)
                .ticketId(ticketId)
                .status(status)
                .createdAt(createdAt)
                .completedAt(completedAt)
                .build();
        wo.setPropertyId(propertyId);
        return wo;
    }

    private Ticket createTicket(UUID ticketId, SlaStatus slaStatus) {
        Ticket ticket = Ticket.builder()
                .id(ticketId)
                .submittedBy(UUID.randomUUID())
                .unitId(UUID.randomUUID())
                .referenceNumber("SR-2025-" + String.format("%06d", (int) (Math.random() * 999999)))
                .title("Test ticket")
                .description("Description")
                .category(Category.PLUMBING)
                .priority(Priority.HIGH)
                .status(TicketStatus.IN_PROGRESS)
                .slaStatus(slaStatus)
                .createdAt(now.minus(5, ChronoUnit.DAYS))
                .updatedAt(now)
                .build();
        ticket.setPropertyId(propertyId);
        return ticket;
    }

    @Nested
    @DisplayName("Report Generation")
    class ReportGenerationTests {

        @Test
        @DisplayName("should return empty metrics when no vendors exist")
        void noVendors() {
            when(vendorRepository.findByPropertyId(propertyId)).thenReturn(List.of());
            when(workOrderRepository.findByPropertyId(propertyId)).thenReturn(List.of());
            when(ticketRepository.findByPropertyId(propertyId)).thenReturn(List.of());

            VendorPerformanceReportResponse report = vendorPerformanceService.generateReport(propertyId);

            assertThat(report.propertyId()).isEqualTo(propertyId);
            assertThat(report.totalVendors()).isEqualTo(0);
            assertThat(report.vendors()).isEmpty();
        }

        @Test
        @DisplayName("should return vendor with zero work orders")
        void vendorWithNoWorkOrders() {
            UUID vendorId = UUID.randomUUID();
            Vendor vendor = createVendor(vendorId, "AcmePlumbing");

            when(vendorRepository.findByPropertyId(propertyId)).thenReturn(List.of(vendor));
            when(workOrderRepository.findByPropertyId(propertyId)).thenReturn(List.of());
            when(ticketRepository.findByPropertyId(propertyId)).thenReturn(List.of());

            VendorPerformanceReportResponse report = vendorPerformanceService.generateReport(propertyId);

            assertThat(report.totalVendors()).isEqualTo(1);
            VendorMetrics metrics = report.vendors().get(0);
            assertThat(metrics.vendorId()).isEqualTo(vendorId);
            assertThat(metrics.vendorName()).isEqualTo("AcmePlumbing");
            assertThat(metrics.totalWorkOrders()).isEqualTo(0);
            assertThat(metrics.completedWorkOrders()).isEqualTo(0);
            assertThat(metrics.averageResolutionTimeHours()).isEqualTo(0.0);
            assertThat(metrics.slaCompliancePercent()).isCloseTo(100.0, within(0.01));
        }

        @Test
        @DisplayName("should compute average resolution time from completed work orders")
        void computeAverageResolutionTime() {
            UUID vendorId = UUID.randomUUID();
            Vendor vendor = createVendor(vendorId, "FastFix");

            UUID ticketId1 = UUID.randomUUID();
            UUID ticketId2 = UUID.randomUUID();

            // Work order 1: completed in 24 hours
            Instant wo1Created = now.minus(3, ChronoUnit.DAYS);
            Instant wo1Completed = wo1Created.plus(24, ChronoUnit.HOURS);
            WorkOrder wo1 = createWorkOrder(vendorId, ticketId1, WorkOrderStatus.COMPLETED, wo1Created, wo1Completed);

            // Work order 2: completed in 48 hours
            Instant wo2Created = now.minus(5, ChronoUnit.DAYS);
            Instant wo2Completed = wo2Created.plus(48, ChronoUnit.HOURS);
            WorkOrder wo2 = createWorkOrder(vendorId, ticketId2, WorkOrderStatus.COMPLETED, wo2Created, wo2Completed);

            Ticket t1 = createTicket(ticketId1, SlaStatus.ON_TRACK);
            Ticket t2 = createTicket(ticketId2, SlaStatus.ON_TRACK);

            when(vendorRepository.findByPropertyId(propertyId)).thenReturn(List.of(vendor));
            when(workOrderRepository.findByPropertyId(propertyId)).thenReturn(List.of(wo1, wo2));
            when(ticketRepository.findByPropertyId(propertyId)).thenReturn(List.of(t1, t2));

            VendorPerformanceReportResponse report = vendorPerformanceService.generateReport(propertyId);

            VendorMetrics metrics = report.vendors().get(0);
            assertThat(metrics.completedWorkOrders()).isEqualTo(2);
            // Average: (24 + 48) / 2 = 36 hours
            assertThat(metrics.averageResolutionTimeHours()).isCloseTo(36.0, within(0.01));
        }

        @Test
        @DisplayName("should compute SLA compliance rate per vendor")
        void computeSlaComplianceRate() {
            UUID vendorId = UUID.randomUUID();
            Vendor vendor = createVendor(vendorId, "ReliableServices");

            UUID ticketId1 = UUID.randomUUID();
            UUID ticketId2 = UUID.randomUUID();
            UUID ticketId3 = UUID.randomUUID();

            WorkOrder wo1 = createWorkOrder(vendorId, ticketId1, WorkOrderStatus.COMPLETED,
                    now.minus(5, ChronoUnit.DAYS), now.minus(4, ChronoUnit.DAYS));
            WorkOrder wo2 = createWorkOrder(vendorId, ticketId2, WorkOrderStatus.COMPLETED,
                    now.minus(5, ChronoUnit.DAYS), now.minus(3, ChronoUnit.DAYS));
            WorkOrder wo3 = createWorkOrder(vendorId, ticketId3, WorkOrderStatus.IN_PROGRESS,
                    now.minus(2, ChronoUnit.DAYS), null);

            // 2 compliant, 1 breached
            Ticket t1 = createTicket(ticketId1, SlaStatus.ON_TRACK);
            Ticket t2 = createTicket(ticketId2, SlaStatus.RESOLUTION_BREACHED);
            Ticket t3 = createTicket(ticketId3, SlaStatus.ON_TRACK);

            when(vendorRepository.findByPropertyId(propertyId)).thenReturn(List.of(vendor));
            when(workOrderRepository.findByPropertyId(propertyId)).thenReturn(List.of(wo1, wo2, wo3));
            when(ticketRepository.findByPropertyId(propertyId)).thenReturn(List.of(t1, t2, t3));

            VendorPerformanceReportResponse report = vendorPerformanceService.generateReport(propertyId);

            VendorMetrics metrics = report.vendors().get(0);
            assertThat(metrics.totalWorkOrders()).isEqualTo(3);
            assertThat(metrics.completedWorkOrders()).isEqualTo(2);
            assertThat(metrics.slaCompliant()).isEqualTo(2);
            assertThat(metrics.slaBreached()).isEqualTo(1);
            // 2 out of 3 => 66.67%
            assertThat(metrics.slaCompliancePercent()).isCloseTo(66.67, within(0.01));
        }

        @Test
        @DisplayName("should handle multiple vendors independently")
        void multipleVendors() {
            UUID vendorId1 = UUID.randomUUID();
            UUID vendorId2 = UUID.randomUUID();
            Vendor vendor1 = createVendor(vendorId1, "VendorA");
            Vendor vendor2 = createVendor(vendorId2, "VendorB");

            UUID ticketId1 = UUID.randomUUID();
            UUID ticketId2 = UUID.randomUUID();

            WorkOrder wo1 = createWorkOrder(vendorId1, ticketId1, WorkOrderStatus.COMPLETED,
                    now.minus(3, ChronoUnit.DAYS), now.minus(2, ChronoUnit.DAYS));
            WorkOrder wo2 = createWorkOrder(vendorId2, ticketId2, WorkOrderStatus.COMPLETED,
                    now.minus(4, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS));

            Ticket t1 = createTicket(ticketId1, SlaStatus.ON_TRACK);
            Ticket t2 = createTicket(ticketId2, SlaStatus.RESOLUTION_BREACHED);

            when(vendorRepository.findByPropertyId(propertyId)).thenReturn(List.of(vendor1, vendor2));
            when(workOrderRepository.findByPropertyId(propertyId)).thenReturn(List.of(wo1, wo2));
            when(ticketRepository.findByPropertyId(propertyId)).thenReturn(List.of(t1, t2));

            VendorPerformanceReportResponse report = vendorPerformanceService.generateReport(propertyId);

            assertThat(report.totalVendors()).isEqualTo(2);

            VendorMetrics metricsA = report.vendors().stream()
                    .filter(m -> m.vendorId().equals(vendorId1))
                    .findFirst().orElseThrow();
            assertThat(metricsA.totalWorkOrders()).isEqualTo(1);
            assertThat(metricsA.completedWorkOrders()).isEqualTo(1);
            assertThat(metricsA.slaCompliancePercent()).isCloseTo(100.0, within(0.01));
            // 24 hours
            assertThat(metricsA.averageResolutionTimeHours()).isCloseTo(24.0, within(0.01));

            VendorMetrics metricsB = report.vendors().stream()
                    .filter(m -> m.vendorId().equals(vendorId2))
                    .findFirst().orElseThrow();
            assertThat(metricsB.totalWorkOrders()).isEqualTo(1);
            assertThat(metricsB.completedWorkOrders()).isEqualTo(1);
            assertThat(metricsB.slaCompliancePercent()).isCloseTo(0.0, within(0.01));
            // 72 hours (3 days)
            assertThat(metricsB.averageResolutionTimeHours()).isCloseTo(72.0, within(0.01));
        }

        @Test
        @DisplayName("should not count in-progress work orders in resolution time average")
        void excludeInProgressFromResolutionTime() {
            UUID vendorId = UUID.randomUUID();
            Vendor vendor = createVendor(vendorId, "OngoingVendor");

            UUID ticketId1 = UUID.randomUUID();
            UUID ticketId2 = UUID.randomUUID();

            // Completed in 12 hours
            WorkOrder woCompleted = createWorkOrder(vendorId, ticketId1, WorkOrderStatus.COMPLETED,
                    now.minus(2, ChronoUnit.DAYS), now.minus(2, ChronoUnit.DAYS).plus(12, ChronoUnit.HOURS));

            // Still in progress
            WorkOrder woInProgress = createWorkOrder(vendorId, ticketId2, WorkOrderStatus.IN_PROGRESS,
                    now.minus(1, ChronoUnit.DAYS), null);

            Ticket t1 = createTicket(ticketId1, SlaStatus.ON_TRACK);
            Ticket t2 = createTicket(ticketId2, SlaStatus.ON_TRACK);

            when(vendorRepository.findByPropertyId(propertyId)).thenReturn(List.of(vendor));
            when(workOrderRepository.findByPropertyId(propertyId)).thenReturn(List.of(woCompleted, woInProgress));
            when(ticketRepository.findByPropertyId(propertyId)).thenReturn(List.of(t1, t2));

            VendorPerformanceReportResponse report = vendorPerformanceService.generateReport(propertyId);

            VendorMetrics metrics = report.vendors().get(0);
            assertThat(metrics.totalWorkOrders()).isEqualTo(2);
            assertThat(metrics.completedWorkOrders()).isEqualTo(1);
            // Only the completed one contributes: 12 hours
            assertThat(metrics.averageResolutionTimeHours()).isCloseTo(12.0, within(0.01));
        }
    }
}
