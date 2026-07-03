package com.strataresolve.reporting.service;

import com.strataresolve.reporting.dto.SlaComplianceReportResponse;
import com.strataresolve.reporting.dto.SlaComplianceReportResponse.CategoryBreakdown;
import com.strataresolve.ticket.domain.Category;
import com.strataresolve.ticket.domain.Priority;
import com.strataresolve.ticket.domain.SlaStatus;
import com.strataresolve.ticket.domain.Ticket;
import com.strataresolve.ticket.domain.TicketStatus;
import com.strataresolve.ticket.repository.TicketRepository;
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
@DisplayName("SlaReportService")
class SlaReportServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    private SlaReportService slaReportService;

    private UUID propertyId;
    private Instant now;

    @BeforeEach
    void setUp() {
        slaReportService = new SlaReportService(ticketRepository);
        propertyId = UUID.randomUUID();
        now = Instant.now();
    }

    private Ticket createTicket(Category category, Priority priority, SlaStatus slaStatus,
                                 Instant ackDueAt, Instant acknowledgedAt,
                                 Instant resDueAt, Instant resolvedAt,
                                 Instant createdAt) {
        Ticket ticket = Ticket.builder()
                .id(UUID.randomUUID())
                .submittedBy(UUID.randomUUID())
                .unitId(UUID.randomUUID())
                .referenceNumber("SR-2025-" + String.format("%06d", (int) (Math.random() * 999999)))
                .title("Test ticket")
                .description("Description")
                .category(category)
                .priority(priority)
                .status(TicketStatus.IN_PROGRESS)
                .slaStatus(slaStatus)
                .acknowledgementDueAt(ackDueAt)
                .acknowledgedAt(acknowledgedAt)
                .resolutionDueAt(resDueAt)
                .resolvedAt(resolvedAt)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
        ticket.setPropertyId(propertyId);
        return ticket;
    }

    @Nested
    @DisplayName("Compliance Percentage Calculations")
    class ComplianceTests {

        @Test
        @DisplayName("should return 100% compliance when all tickets are on track")
        void allOnTrack() {
            Instant ackDue = now.plus(4, ChronoUnit.HOURS);
            Instant resDue = now.plus(24, ChronoUnit.HOURS);
            Instant ackedAt = now.plus(2, ChronoUnit.HOURS);
            Instant resolvedAtTime = now.plus(20, ChronoUnit.HOURS);

            Ticket t1 = createTicket(Category.PLUMBING, Priority.HIGH, SlaStatus.ON_TRACK,
                    ackDue, ackedAt, resDue, resolvedAtTime, now.minus(1, ChronoUnit.DAYS));
            Ticket t2 = createTicket(Category.ELECTRICAL, Priority.NORMAL, SlaStatus.ON_TRACK,
                    ackDue, ackedAt, resDue, resolvedAtTime, now.minus(1, ChronoUnit.DAYS));

            when(ticketRepository.findByPropertyId(propertyId)).thenReturn(List.of(t1, t2));

            SlaComplianceReportResponse report = slaReportService.generateReport(propertyId, null, null, null, null);

            assertThat(report.totalTickets()).isEqualTo(2);
            assertThat(report.acknowledgementCompliancePercent()).isCloseTo(100.0, within(0.01));
            assertThat(report.resolutionCompliancePercent()).isCloseTo(100.0, within(0.01));
            assertThat(report.acknowledgementCompliant()).isEqualTo(2);
            assertThat(report.acknowledgementBreached()).isEqualTo(0);
            assertThat(report.resolutionCompliant()).isEqualTo(2);
            assertThat(report.resolutionBreached()).isEqualTo(0);
        }

        @Test
        @DisplayName("should compute correct percentage when some tickets are breached")
        void mixedCompliance() {
            Instant ackDue = now.minus(1, ChronoUnit.HOURS);
            Instant resDue = now.minus(1, ChronoUnit.HOURS);

            // Ticket 1: ack compliant, resolution compliant
            Ticket t1 = createTicket(Category.PLUMBING, Priority.HIGH, SlaStatus.ON_TRACK,
                    ackDue, ackDue.minus(30, ChronoUnit.MINUTES), resDue, resDue.minus(30, ChronoUnit.MINUTES),
                    now.minus(2, ChronoUnit.DAYS));

            // Ticket 2: ack breached (acknowledged after deadline)
            Ticket t2 = createTicket(Category.PLUMBING, Priority.NORMAL, SlaStatus.ACK_BREACHED,
                    ackDue, ackDue.plus(1, ChronoUnit.HOURS), resDue, resDue.minus(30, ChronoUnit.MINUTES),
                    now.minus(2, ChronoUnit.DAYS));

            // Ticket 3: resolution breached (not yet resolved, sla_status indicates breach)
            Ticket t3 = createTicket(Category.ELECTRICAL, Priority.HIGH, SlaStatus.RESOLUTION_BREACHED,
                    ackDue, ackDue.minus(10, ChronoUnit.MINUTES), resDue, null,
                    now.minus(3, ChronoUnit.DAYS));

            // Ticket 4: both breached
            Ticket t4 = createTicket(Category.ELECTRICAL, Priority.URGENT, SlaStatus.BOTH_BREACHED,
                    ackDue, ackDue.plus(2, ChronoUnit.HOURS), resDue, null,
                    now.minus(3, ChronoUnit.DAYS));

            when(ticketRepository.findByPropertyId(propertyId)).thenReturn(List.of(t1, t2, t3, t4));

            SlaComplianceReportResponse report = slaReportService.generateReport(propertyId, null, null, null, null);

            assertThat(report.totalTickets()).isEqualTo(4);
            // Ack: t1 compliant, t2 breached (acked after due), t3 compliant (acked before due), t4 breached (acked after due)
            assertThat(report.acknowledgementCompliant()).isEqualTo(2);
            assertThat(report.acknowledgementBreached()).isEqualTo(2);
            assertThat(report.acknowledgementCompliancePercent()).isCloseTo(50.0, within(0.01));

            // Res: t1 compliant (resolved before due), t2 compliant (resolved before due), t3 breached (status), t4 breached (status)
            assertThat(report.resolutionCompliant()).isEqualTo(2);
            assertThat(report.resolutionBreached()).isEqualTo(2);
            assertThat(report.resolutionCompliancePercent()).isCloseTo(50.0, within(0.01));
        }

        @Test
        @DisplayName("should return 100% compliance when there are no tickets")
        void noTickets() {
            when(ticketRepository.findByPropertyId(propertyId)).thenReturn(List.of());

            SlaComplianceReportResponse report = slaReportService.generateReport(propertyId, null, null, null, null);

            assertThat(report.totalTickets()).isEqualTo(0);
            assertThat(report.acknowledgementCompliancePercent()).isCloseTo(100.0, within(0.01));
            assertThat(report.resolutionCompliancePercent()).isCloseTo(100.0, within(0.01));
        }
    }

    @Nested
    @DisplayName("Filtering")
    class FilteringTests {

        @Test
        @DisplayName("should filter by date range")
        void filterByDateRange() {
            Instant rangeStart = now.minus(7, ChronoUnit.DAYS);
            Instant rangeEnd = now.minus(1, ChronoUnit.DAYS);

            Instant ackDue = now.plus(4, ChronoUnit.HOURS);
            Instant resDue = now.plus(24, ChronoUnit.HOURS);
            Instant ackedAt = now.plus(2, ChronoUnit.HOURS);

            // Within range
            Ticket inRange = createTicket(Category.PLUMBING, Priority.HIGH, SlaStatus.ON_TRACK,
                    ackDue, ackedAt, resDue, now.plus(10, ChronoUnit.HOURS),
                    now.minus(5, ChronoUnit.DAYS));

            // Outside range (too old)
            Ticket tooOld = createTicket(Category.PLUMBING, Priority.HIGH, SlaStatus.ON_TRACK,
                    ackDue, ackedAt, resDue, now.plus(10, ChronoUnit.HOURS),
                    now.minus(10, ChronoUnit.DAYS));

            // Outside range (too recent)
            Ticket tooRecent = createTicket(Category.PLUMBING, Priority.HIGH, SlaStatus.ON_TRACK,
                    ackDue, ackedAt, resDue, now.plus(10, ChronoUnit.HOURS),
                    now);

            when(ticketRepository.findByPropertyId(propertyId)).thenReturn(List.of(inRange, tooOld, tooRecent));

            SlaComplianceReportResponse report = slaReportService.generateReport(propertyId, rangeStart, rangeEnd, null, null);

            assertThat(report.totalTickets()).isEqualTo(1);
            assertThat(report.from()).isEqualTo(rangeStart);
            assertThat(report.to()).isEqualTo(rangeEnd);
        }

        @Test
        @DisplayName("should filter by category")
        void filterByCategory() {
            Instant ackDue = now.plus(4, ChronoUnit.HOURS);
            Instant resDue = now.plus(24, ChronoUnit.HOURS);
            Instant ackedAt = now.plus(2, ChronoUnit.HOURS);

            Ticket plumbing = createTicket(Category.PLUMBING, Priority.HIGH, SlaStatus.ON_TRACK,
                    ackDue, ackedAt, resDue, now.plus(10, ChronoUnit.HOURS), now.minus(1, ChronoUnit.DAYS));
            Ticket electrical = createTicket(Category.ELECTRICAL, Priority.HIGH, SlaStatus.ON_TRACK,
                    ackDue, ackedAt, resDue, now.plus(10, ChronoUnit.HOURS), now.minus(1, ChronoUnit.DAYS));

            when(ticketRepository.findByPropertyId(propertyId)).thenReturn(List.of(plumbing, electrical));

            SlaComplianceReportResponse report = slaReportService.generateReport(propertyId, null, null, Category.PLUMBING, null);

            assertThat(report.totalTickets()).isEqualTo(1);
            assertThat(report.categoryFilter()).isEqualTo(Category.PLUMBING);
        }

        @Test
        @DisplayName("should filter by priority")
        void filterByPriority() {
            Instant ackDue = now.plus(4, ChronoUnit.HOURS);
            Instant resDue = now.plus(24, ChronoUnit.HOURS);
            Instant ackedAt = now.plus(2, ChronoUnit.HOURS);

            Ticket high = createTicket(Category.PLUMBING, Priority.HIGH, SlaStatus.ON_TRACK,
                    ackDue, ackedAt, resDue, now.plus(10, ChronoUnit.HOURS), now.minus(1, ChronoUnit.DAYS));
            Ticket low = createTicket(Category.PLUMBING, Priority.LOW, SlaStatus.ON_TRACK,
                    ackDue, ackedAt, resDue, now.plus(10, ChronoUnit.HOURS), now.minus(1, ChronoUnit.DAYS));

            when(ticketRepository.findByPropertyId(propertyId)).thenReturn(List.of(high, low));

            SlaComplianceReportResponse report = slaReportService.generateReport(propertyId, null, null, null, Priority.HIGH);

            assertThat(report.totalTickets()).isEqualTo(1);
            assertThat(report.priorityFilter()).isEqualTo(Priority.HIGH);
        }
    }

    @Nested
    @DisplayName("Category Breakdowns")
    class CategoryBreakdownTests {

        @Test
        @DisplayName("should generate breakdowns per category")
        void perCategoryBreakdowns() {
            Instant ackDue = now.minus(1, ChronoUnit.HOURS);
            Instant resDue = now.minus(1, ChronoUnit.HOURS);

            // Plumbing: 1 compliant, 1 breached
            Ticket p1 = createTicket(Category.PLUMBING, Priority.HIGH, SlaStatus.ON_TRACK,
                    ackDue, ackDue.minus(30, ChronoUnit.MINUTES), resDue, resDue.minus(30, ChronoUnit.MINUTES),
                    now.minus(2, ChronoUnit.DAYS));
            Ticket p2 = createTicket(Category.PLUMBING, Priority.NORMAL, SlaStatus.BOTH_BREACHED,
                    ackDue, ackDue.plus(1, ChronoUnit.HOURS), resDue, null,
                    now.minus(2, ChronoUnit.DAYS));

            // Electrical: all compliant
            Ticket e1 = createTicket(Category.ELECTRICAL, Priority.HIGH, SlaStatus.ON_TRACK,
                    ackDue, ackDue.minus(10, ChronoUnit.MINUTES), resDue, resDue.minus(10, ChronoUnit.MINUTES),
                    now.minus(2, ChronoUnit.DAYS));

            when(ticketRepository.findByPropertyId(propertyId)).thenReturn(List.of(p1, p2, e1));

            SlaComplianceReportResponse report = slaReportService.generateReport(propertyId, null, null, null, null);

            assertThat(report.categoryBreakdowns()).hasSize(2);

            CategoryBreakdown plumbing = report.categoryBreakdowns().stream()
                    .filter(b -> b.category() == Category.PLUMBING)
                    .findFirst().orElseThrow();
            assertThat(plumbing.totalTickets()).isEqualTo(2);
            assertThat(plumbing.acknowledgementCompliancePercent()).isCloseTo(50.0, within(0.01));
            assertThat(plumbing.resolutionCompliancePercent()).isCloseTo(50.0, within(0.01));

            CategoryBreakdown electrical = report.categoryBreakdowns().stream()
                    .filter(b -> b.category() == Category.ELECTRICAL)
                    .findFirst().orElseThrow();
            assertThat(electrical.totalTickets()).isEqualTo(1);
            assertThat(electrical.acknowledgementCompliancePercent()).isCloseTo(100.0, within(0.01));
            assertThat(electrical.resolutionCompliancePercent()).isCloseTo(100.0, within(0.01));
        }
    }

    @Nested
    @DisplayName("Acknowledgement Compliance Logic")
    class AcknowledgementComplianceTests {

        @Test
        @DisplayName("should be compliant when acknowledged before deadline")
        void acknowledgedBeforeDeadline() {
            Instant ackDue = now.plus(4, ChronoUnit.HOURS);
            Ticket ticket = createTicket(Category.PLUMBING, Priority.HIGH, SlaStatus.ON_TRACK,
                    ackDue, ackDue.minus(1, ChronoUnit.HOURS), null, null, now);

            assertThat(slaReportService.isAcknowledgementCompliant(ticket)).isTrue();
        }

        @Test
        @DisplayName("should be compliant when acknowledged exactly at deadline")
        void acknowledgedAtDeadline() {
            Instant ackDue = now.plus(4, ChronoUnit.HOURS);
            Ticket ticket = createTicket(Category.PLUMBING, Priority.HIGH, SlaStatus.ON_TRACK,
                    ackDue, ackDue, null, null, now);

            assertThat(slaReportService.isAcknowledgementCompliant(ticket)).isTrue();
        }

        @Test
        @DisplayName("should be breached when acknowledged after deadline")
        void acknowledgedAfterDeadline() {
            Instant ackDue = now.plus(4, ChronoUnit.HOURS);
            Ticket ticket = createTicket(Category.PLUMBING, Priority.HIGH, SlaStatus.ACK_BREACHED,
                    ackDue, ackDue.plus(1, ChronoUnit.HOURS), null, null, now);

            assertThat(slaReportService.isAcknowledgementCompliant(ticket)).isFalse();
        }

        @Test
        @DisplayName("should be breached when not acknowledged and sla status shows breach")
        void notAcknowledgedAndBreached() {
            Instant ackDue = now.minus(1, ChronoUnit.HOURS);
            Ticket ticket = createTicket(Category.PLUMBING, Priority.HIGH, SlaStatus.ACK_BREACHED,
                    ackDue, null, null, null, now.minus(1, ChronoUnit.DAYS));

            assertThat(slaReportService.isAcknowledgementCompliant(ticket)).isFalse();
        }

        @Test
        @DisplayName("should be compliant when not acknowledged but still on track")
        void notAcknowledgedButOnTrack() {
            Instant ackDue = now.plus(4, ChronoUnit.HOURS);
            Ticket ticket = createTicket(Category.PLUMBING, Priority.HIGH, SlaStatus.ON_TRACK,
                    ackDue, null, null, null, now);

            assertThat(slaReportService.isAcknowledgementCompliant(ticket)).isTrue();
        }
    }

    @Nested
    @DisplayName("Resolution Compliance Logic")
    class ResolutionComplianceTests {

        @Test
        @DisplayName("should be compliant when resolved before deadline")
        void resolvedBeforeDeadline() {
            Instant resDue = now.plus(24, ChronoUnit.HOURS);
            Ticket ticket = createTicket(Category.PLUMBING, Priority.HIGH, SlaStatus.ON_TRACK,
                    null, null, resDue, resDue.minus(2, ChronoUnit.HOURS), now);

            assertThat(slaReportService.isResolutionCompliant(ticket)).isTrue();
        }

        @Test
        @DisplayName("should be breached when resolved after deadline")
        void resolvedAfterDeadline() {
            Instant resDue = now.plus(24, ChronoUnit.HOURS);
            Ticket ticket = createTicket(Category.PLUMBING, Priority.HIGH, SlaStatus.RESOLUTION_BREACHED,
                    null, null, resDue, resDue.plus(1, ChronoUnit.HOURS), now);

            assertThat(slaReportService.isResolutionCompliant(ticket)).isFalse();
        }

        @Test
        @DisplayName("should be breached when not resolved and status shows resolution breach")
        void notResolvedAndBreached() {
            Instant resDue = now.minus(1, ChronoUnit.HOURS);
            Ticket ticket = createTicket(Category.PLUMBING, Priority.HIGH, SlaStatus.RESOLUTION_BREACHED,
                    null, null, resDue, null, now.minus(2, ChronoUnit.DAYS));

            assertThat(slaReportService.isResolutionCompliant(ticket)).isFalse();
        }

        @Test
        @DisplayName("should be compliant when not resolved but on track")
        void notResolvedButOnTrack() {
            Instant resDue = now.plus(24, ChronoUnit.HOURS);
            Ticket ticket = createTicket(Category.PLUMBING, Priority.HIGH, SlaStatus.ON_TRACK,
                    null, null, resDue, null, now);

            assertThat(slaReportService.isResolutionCompliant(ticket)).isTrue();
        }
    }
}
