package com.strataresolve.sla.service;

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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SlaMonitorScheduler}.
 * Validates Requirement 14.3: SLA breach detection and status updates.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SlaMonitorScheduler")
class SlaMonitorSchedulerTest {

    @Mock
    private TicketRepository ticketRepository;

    private SlaMonitorScheduler scheduler;

    private static final UUID PROPERTY_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID UNIT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        scheduler = new SlaMonitorScheduler(ticketRepository);
    }

    private Ticket createTicket(Instant ackDueAt, Instant resDueAt,
                                Instant acknowledgedAt, Instant resolvedAt,
                                SlaStatus currentSlaStatus) {
        Ticket ticket = Ticket.builder()
                .id(UUID.randomUUID())
                .submittedBy(USER_ID)
                .unitId(UNIT_ID)
                .referenceNumber("SR-2025-000001")
                .title("Test Ticket")
                .description("Test description")
                .category(Category.PLUMBING)
                .priority(Priority.HIGH)
                .status(TicketStatus.SUBMITTED)
                .acknowledgementDueAt(ackDueAt)
                .resolutionDueAt(resDueAt)
                .acknowledgedAt(acknowledgedAt)
                .resolvedAt(resolvedAt)
                .slaStatus(currentSlaStatus)
                .build();
        ticket.setPropertyId(PROPERTY_ID);
        return ticket;
    }

    @Nested
    @DisplayName("determineSlaStatus")
    class DetermineSlaStatusTests {

        @Test
        @DisplayName("should return ACK_BREACHED when only acknowledgement is overdue")
        void shouldReturnAckBreachedWhenOnlyAckOverdue() {
            Instant now = Instant.now();
            Ticket ticket = createTicket(
                    now.minus(1, ChronoUnit.HOURS),  // ack due in the past
                    now.plus(24, ChronoUnit.HOURS),  // res due in the future
                    null,                             // not acknowledged
                    null,                             // not resolved
                    SlaStatus.ON_TRACK
            );

            SlaStatus result = scheduler.determineSlaStatus(ticket, now);
            assertThat(result).isEqualTo(SlaStatus.ACK_BREACHED);
        }

        @Test
        @DisplayName("should return RESOLUTION_BREACHED when only resolution is overdue")
        void shouldReturnResolutionBreachedWhenOnlyResOverdue() {
            Instant now = Instant.now();
            Ticket ticket = createTicket(
                    now.minus(5, ChronoUnit.HOURS),   // ack due in the past
                    now.minus(1, ChronoUnit.HOURS),   // res due in the past
                    now.minus(4, ChronoUnit.HOURS),   // acknowledged (so ack not breached)
                    null,                              // not resolved
                    SlaStatus.ON_TRACK
            );

            SlaStatus result = scheduler.determineSlaStatus(ticket, now);
            assertThat(result).isEqualTo(SlaStatus.RESOLUTION_BREACHED);
        }

        @Test
        @DisplayName("should return BOTH_BREACHED when both ack and resolution are overdue")
        void shouldReturnBothBreachedWhenBothOverdue() {
            Instant now = Instant.now();
            Ticket ticket = createTicket(
                    now.minus(5, ChronoUnit.HOURS),  // ack due in the past
                    now.minus(1, ChronoUnit.HOURS),  // res due in the past
                    null,                             // not acknowledged
                    null,                             // not resolved
                    SlaStatus.ON_TRACK
            );

            SlaStatus result = scheduler.determineSlaStatus(ticket, now);
            assertThat(result).isEqualTo(SlaStatus.BOTH_BREACHED);
        }

        @Test
        @DisplayName("should return ON_TRACK when neither is breached")
        void shouldReturnOnTrackWhenNeitherBreached() {
            Instant now = Instant.now();
            Ticket ticket = createTicket(
                    now.plus(4, ChronoUnit.HOURS),   // ack due in the future
                    now.plus(24, ChronoUnit.HOURS),  // res due in the future
                    null,                             // not acknowledged
                    null,                             // not resolved
                    SlaStatus.ON_TRACK
            );

            SlaStatus result = scheduler.determineSlaStatus(ticket, now);
            assertThat(result).isEqualTo(SlaStatus.ON_TRACK);
        }

        @Test
        @DisplayName("should return ON_TRACK when ack is overdue but already acknowledged")
        void shouldReturnOnTrackWhenAckOverdueButAcknowledged() {
            Instant now = Instant.now();
            Ticket ticket = createTicket(
                    now.minus(1, ChronoUnit.HOURS),   // ack due in the past
                    now.plus(24, ChronoUnit.HOURS),   // res due in the future
                    now.minus(2, ChronoUnit.HOURS),   // acknowledged before deadline (late but still acknowledged)
                    null,                              // not resolved
                    SlaStatus.ON_TRACK
            );

            SlaStatus result = scheduler.determineSlaStatus(ticket, now);
            assertThat(result).isEqualTo(SlaStatus.ON_TRACK);
        }

        @Test
        @DisplayName("should return ON_TRACK when resolution is overdue but already resolved")
        void shouldReturnOnTrackWhenResOverdueButResolved() {
            Instant now = Instant.now();
            Ticket ticket = createTicket(
                    now.minus(5, ChronoUnit.HOURS),   // ack due in the past
                    now.minus(1, ChronoUnit.HOURS),   // res due in the past
                    now.minus(4, ChronoUnit.HOURS),   // acknowledged
                    now.minus(30, ChronoUnit.MINUTES), // resolved
                    SlaStatus.ON_TRACK
            );

            SlaStatus result = scheduler.determineSlaStatus(ticket, now);
            assertThat(result).isEqualTo(SlaStatus.ON_TRACK);
        }

        @Test
        @DisplayName("should return ACK_BREACHED when ack is overdue and resolution due is null")
        void shouldReturnAckBreachedWhenResDueIsNull() {
            Instant now = Instant.now();
            Ticket ticket = createTicket(
                    now.minus(1, ChronoUnit.HOURS),  // ack due in the past
                    null,                             // no resolution due
                    null,                             // not acknowledged
                    null,                             // not resolved
                    SlaStatus.ON_TRACK
            );

            SlaStatus result = scheduler.determineSlaStatus(ticket, now);
            assertThat(result).isEqualTo(SlaStatus.ACK_BREACHED);
        }

        @Test
        @DisplayName("should return RESOLUTION_BREACHED when resolution overdue and ack due is null")
        void shouldReturnResBreachedWhenAckDueIsNull() {
            Instant now = Instant.now();
            Ticket ticket = createTicket(
                    null,                             // no ack due
                    now.minus(1, ChronoUnit.HOURS),  // res due in the past
                    null,                             // not acknowledged
                    null,                             // not resolved
                    SlaStatus.ON_TRACK
            );

            SlaStatus result = scheduler.determineSlaStatus(ticket, now);
            assertThat(result).isEqualTo(SlaStatus.RESOLUTION_BREACHED);
        }
    }

    @Nested
    @DisplayName("detectBreachesAtTime")
    class DetectBreachesAtTimeTests {

        @Test
        @DisplayName("should return 0 when no tickets have breached SLA")
        void shouldReturnZeroWhenNoBreaches() {
            Instant now = Instant.now();
            when(ticketRepository.findTicketsWithBreachedSla(now))
                    .thenReturn(Collections.emptyList());

            int result = scheduler.detectBreachesAtTime(now);

            assertThat(result).isZero();
            verify(ticketRepository, never()).save(any());
        }

        @Test
        @DisplayName("should update ticket sla_status when breach detected")
        void shouldUpdateTicketWhenBreachDetected() {
            Instant now = Instant.now();
            Ticket ticket = createTicket(
                    now.minus(1, ChronoUnit.HOURS),
                    now.plus(24, ChronoUnit.HOURS),
                    null,
                    null,
                    SlaStatus.ON_TRACK
            );

            when(ticketRepository.findTicketsWithBreachedSla(now))
                    .thenReturn(List.of(ticket));
            when(ticketRepository.save(any(Ticket.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            int result = scheduler.detectBreachesAtTime(now);

            assertThat(result).isEqualTo(1);
            ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
            verify(ticketRepository).save(captor.capture());
            assertThat(captor.getValue().getSlaStatus()).isEqualTo(SlaStatus.ACK_BREACHED);
        }

        @Test
        @DisplayName("should not save ticket if sla_status has not changed")
        void shouldNotSaveIfStatusUnchanged() {
            Instant now = Instant.now();
            Ticket ticket = createTicket(
                    now.minus(1, ChronoUnit.HOURS),
                    now.plus(24, ChronoUnit.HOURS),
                    null,
                    null,
                    SlaStatus.ACK_BREACHED  // already marked as breached
            );

            when(ticketRepository.findTicketsWithBreachedSla(now))
                    .thenReturn(List.of(ticket));

            int result = scheduler.detectBreachesAtTime(now);

            assertThat(result).isZero();
            verify(ticketRepository, never()).save(any());
        }

        @Test
        @DisplayName("should update multiple tickets with different breach types")
        void shouldUpdateMultipleTickets() {
            Instant now = Instant.now();

            Ticket ackBreachedTicket = createTicket(
                    now.minus(1, ChronoUnit.HOURS),
                    now.plus(24, ChronoUnit.HOURS),
                    null,
                    null,
                    SlaStatus.ON_TRACK
            );

            Ticket bothBreachedTicket = createTicket(
                    now.minus(5, ChronoUnit.HOURS),
                    now.minus(1, ChronoUnit.HOURS),
                    null,
                    null,
                    SlaStatus.ACK_BREACHED  // was only ack breached, now both
            );

            when(ticketRepository.findTicketsWithBreachedSla(now))
                    .thenReturn(List.of(ackBreachedTicket, bothBreachedTicket));
            when(ticketRepository.save(any(Ticket.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            int result = scheduler.detectBreachesAtTime(now);

            assertThat(result).isEqualTo(2);
            verify(ticketRepository, times(2)).save(any(Ticket.class));
        }

        @Test
        @DisplayName("should transition from ACK_BREACHED to BOTH_BREACHED when resolution also breaches")
        void shouldTransitionToBothBreached() {
            Instant now = Instant.now();
            Ticket ticket = createTicket(
                    now.minus(5, ChronoUnit.HOURS),   // ack overdue
                    now.minus(1, ChronoUnit.HOURS),   // res also overdue
                    null,                              // not acknowledged
                    null,                              // not resolved
                    SlaStatus.ACK_BREACHED            // previously only ack breached
            );

            when(ticketRepository.findTicketsWithBreachedSla(now))
                    .thenReturn(List.of(ticket));
            when(ticketRepository.save(any(Ticket.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            int result = scheduler.detectBreachesAtTime(now);

            assertThat(result).isEqualTo(1);
            ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
            verify(ticketRepository).save(captor.capture());
            assertThat(captor.getValue().getSlaStatus()).isEqualTo(SlaStatus.BOTH_BREACHED);
        }
    }
}
