package com.strataresolve.reporting.service;

import com.strataresolve.reporting.dto.AgeBracket;
import com.strataresolve.reporting.dto.AgeingBracketEntry;
import com.strataresolve.reporting.dto.AgeingReportResponse;
import com.strataresolve.shared.exception.AccessDeniedException;
import com.strataresolve.ticket.domain.Category;
import com.strataresolve.ticket.domain.Priority;
import com.strataresolve.ticket.domain.Ticket;
import com.strataresolve.ticket.domain.TicketStatus;
import com.strataresolve.ticket.repository.TicketRepository;
import com.strataresolve.user.domain.Membership;
import com.strataresolve.user.domain.Role;
import com.strataresolve.user.repository.MembershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AgeingReportService")
class AgeingReportServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private MembershipRepository membershipRepository;

    private AgeingReportService ageingReportService;

    private UUID propertyId;
    private UUID userId;
    private Instant referenceTime;

    @BeforeEach
    void setUp() {
        ageingReportService = new AgeingReportService(ticketRepository, membershipRepository);
        propertyId = UUID.randomUUID();
        userId = UUID.randomUUID();
        referenceTime = Instant.parse("2025-01-20T12:00:00Z");
    }

    private Membership createMembership(Role role) {
        Membership membership = Membership.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .role(role)
                .isActive(true)
                .effectiveFrom(LocalDate.now())
                .build();
        membership.setPropertyId(propertyId);
        return membership;
    }

    private Ticket createTicket(TicketStatus status, Instant createdAt) {
        Ticket ticket = Ticket.builder()
                .id(UUID.randomUUID())
                .submittedBy(UUID.randomUUID())
                .unitId(UUID.randomUUID())
                .referenceNumber("SR-2025-000001")
                .title("Test ticket")
                .description("Test description")
                .category(Category.PLUMBING)
                .priority(Priority.NORMAL)
                .status(status)
                .location("Test location")
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
        ticket.setPropertyId(propertyId);
        return ticket;
    }

    @Nested
    @DisplayName("Access Control")
    class AccessControlTests {

        @Test
        @DisplayName("should allow access for Property Manager")
        void shouldAllowPropertyManager() {
            when(membershipRepository.findActiveByUserIdAndPropertyId(userId, propertyId))
                    .thenReturn(List.of(createMembership(Role.PROPERTY_MANAGER)));
            when(ticketRepository.findByPropertyId(propertyId)).thenReturn(List.of());

            AgeingReportResponse response = ageingReportService.generateReport(propertyId, userId, referenceTime);

            assertThat(response).isNotNull();
            assertThat(response.propertyId()).isEqualTo(propertyId);
        }

        @Test
        @DisplayName("should allow access for Committee Member")
        void shouldAllowCommitteeMember() {
            when(membershipRepository.findActiveByUserIdAndPropertyId(userId, propertyId))
                    .thenReturn(List.of(createMembership(Role.COMMITTEE_MEMBER)));
            when(ticketRepository.findByPropertyId(propertyId)).thenReturn(List.of());

            AgeingReportResponse response = ageingReportService.generateReport(propertyId, userId, referenceTime);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("should deny access for Resident")
        void shouldDenyResident() {
            when(membershipRepository.findActiveByUserIdAndPropertyId(userId, propertyId))
                    .thenReturn(List.of(createMembership(Role.RESIDENT_OWNER)));

            assertThatThrownBy(() -> ageingReportService.generateReport(propertyId, userId, referenceTime))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("PROPERTY_MANAGER or COMMITTEE_MEMBER");
        }

        @Test
        @DisplayName("should deny access for Technician")
        void shouldDenyTechnician() {
            when(membershipRepository.findActiveByUserIdAndPropertyId(userId, propertyId))
                    .thenReturn(List.of(createMembership(Role.TECHNICIAN)));

            assertThatThrownBy(() -> ageingReportService.generateReport(propertyId, userId, referenceTime))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("should deny access for user with no active membership")
        void shouldDenyNoMembership() {
            when(membershipRepository.findActiveByUserIdAndPropertyId(userId, propertyId))
                    .thenReturn(List.of());

            assertThatThrownBy(() -> ageingReportService.generateReport(propertyId, userId, referenceTime))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("should deny access for Vendor Admin")
        void shouldDenyVendorAdmin() {
            when(membershipRepository.findActiveByUserIdAndPropertyId(userId, propertyId))
                    .thenReturn(List.of(createMembership(Role.VENDOR_ADMIN)));

            assertThatThrownBy(() -> ageingReportService.generateReport(propertyId, userId, referenceTime))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("Bracket Placement")
    class BracketPlacementTests {

        @BeforeEach
        void setUpAccess() {
            when(membershipRepository.findActiveByUserIdAndPropertyId(userId, propertyId))
                    .thenReturn(List.of(createMembership(Role.PROPERTY_MANAGER)));
        }

        @Test
        @DisplayName("should place ticket created today (0 days) in 0-3 bracket")
        void shouldPlaceZeroDaysInFirstBracket() {
            Ticket ticket = createTicket(TicketStatus.SUBMITTED, referenceTime);
            when(ticketRepository.findByPropertyId(propertyId)).thenReturn(List.of(ticket));

            AgeingReportResponse response = ageingReportService.generateReport(propertyId, userId, referenceTime);

            assertThat(findBracket(response, AgeBracket.ZERO_TO_THREE).count()).isEqualTo(1);
            assertThat(findBracket(response, AgeBracket.ZERO_TO_THREE).ticketIds()).contains(ticket.getId());
        }

        @Test
        @DisplayName("should place ticket created 3 days ago in 0-3 bracket")
        void shouldPlaceThreeDaysInFirstBracket() {
            Instant createdAt = referenceTime.minus(3, ChronoUnit.DAYS);
            Ticket ticket = createTicket(TicketStatus.IN_PROGRESS, createdAt);
            when(ticketRepository.findByPropertyId(propertyId)).thenReturn(List.of(ticket));

            AgeingReportResponse response = ageingReportService.generateReport(propertyId, userId, referenceTime);

            assertThat(findBracket(response, AgeBracket.ZERO_TO_THREE).count()).isEqualTo(1);
        }

        @Test
        @DisplayName("should place ticket created 4 days ago in 4-7 bracket")
        void shouldPlaceFourDaysInSecondBracket() {
            Instant createdAt = referenceTime.minus(4, ChronoUnit.DAYS);
            Ticket ticket = createTicket(TicketStatus.ASSIGNED, createdAt);
            when(ticketRepository.findByPropertyId(propertyId)).thenReturn(List.of(ticket));

            AgeingReportResponse response = ageingReportService.generateReport(propertyId, userId, referenceTime);

            assertThat(findBracket(response, AgeBracket.FOUR_TO_SEVEN).count()).isEqualTo(1);
        }

        @Test
        @DisplayName("should place ticket created 7 days ago in 4-7 bracket")
        void shouldPlaceSevenDaysInSecondBracket() {
            Instant createdAt = referenceTime.minus(7, ChronoUnit.DAYS);
            Ticket ticket = createTicket(TicketStatus.ACKNOWLEDGED, createdAt);
            when(ticketRepository.findByPropertyId(propertyId)).thenReturn(List.of(ticket));

            AgeingReportResponse response = ageingReportService.generateReport(propertyId, userId, referenceTime);

            assertThat(findBracket(response, AgeBracket.FOUR_TO_SEVEN).count()).isEqualTo(1);
        }

        @Test
        @DisplayName("should place ticket created 8 days ago in 8-14 bracket")
        void shouldPlaceEightDaysInThirdBracket() {
            Instant createdAt = referenceTime.minus(8, ChronoUnit.DAYS);
            Ticket ticket = createTicket(TicketStatus.IN_PROGRESS, createdAt);
            when(ticketRepository.findByPropertyId(propertyId)).thenReturn(List.of(ticket));

            AgeingReportResponse response = ageingReportService.generateReport(propertyId, userId, referenceTime);

            assertThat(findBracket(response, AgeBracket.EIGHT_TO_FOURTEEN).count()).isEqualTo(1);
        }

        @Test
        @DisplayName("should place ticket created 14 days ago in 8-14 bracket")
        void shouldPlaceFourteenDaysInThirdBracket() {
            Instant createdAt = referenceTime.minus(14, ChronoUnit.DAYS);
            Ticket ticket = createTicket(TicketStatus.AWAITING_VENDOR, createdAt);
            when(ticketRepository.findByPropertyId(propertyId)).thenReturn(List.of(ticket));

            AgeingReportResponse response = ageingReportService.generateReport(propertyId, userId, referenceTime);

            assertThat(findBracket(response, AgeBracket.EIGHT_TO_FOURTEEN).count()).isEqualTo(1);
        }

        @Test
        @DisplayName("should place ticket created 15 days ago in 15-30 bracket")
        void shouldPlaceFifteenDaysInFourthBracket() {
            Instant createdAt = referenceTime.minus(15, ChronoUnit.DAYS);
            Ticket ticket = createTicket(TicketStatus.IN_PROGRESS, createdAt);
            when(ticketRepository.findByPropertyId(propertyId)).thenReturn(List.of(ticket));

            AgeingReportResponse response = ageingReportService.generateReport(propertyId, userId, referenceTime);

            assertThat(findBracket(response, AgeBracket.FIFTEEN_TO_THIRTY).count()).isEqualTo(1);
        }

        @Test
        @DisplayName("should place ticket created 30 days ago in 15-30 bracket")
        void shouldPlaceThirtyDaysInFourthBracket() {
            Instant createdAt = referenceTime.minus(30, ChronoUnit.DAYS);
            Ticket ticket = createTicket(TicketStatus.SUBMITTED, createdAt);
            when(ticketRepository.findByPropertyId(propertyId)).thenReturn(List.of(ticket));

            AgeingReportResponse response = ageingReportService.generateReport(propertyId, userId, referenceTime);

            assertThat(findBracket(response, AgeBracket.FIFTEEN_TO_THIRTY).count()).isEqualTo(1);
        }

        @Test
        @DisplayName("should place ticket created 31 days ago in over-30 bracket")
        void shouldPlaceThirtyOneDaysInFifthBracket() {
            Instant createdAt = referenceTime.minus(31, ChronoUnit.DAYS);
            Ticket ticket = createTicket(TicketStatus.IN_PROGRESS, createdAt);
            when(ticketRepository.findByPropertyId(propertyId)).thenReturn(List.of(ticket));

            AgeingReportResponse response = ageingReportService.generateReport(propertyId, userId, referenceTime);

            assertThat(findBracket(response, AgeBracket.OVER_THIRTY).count()).isEqualTo(1);
        }

        @Test
        @DisplayName("should place ticket created 100 days ago in over-30 bracket")
        void shouldPlaceHundredDaysInFifthBracket() {
            Instant createdAt = referenceTime.minus(100, ChronoUnit.DAYS);
            Ticket ticket = createTicket(TicketStatus.AWAITING_RESIDENT, createdAt);
            when(ticketRepository.findByPropertyId(propertyId)).thenReturn(List.of(ticket));

            AgeingReportResponse response = ageingReportService.generateReport(propertyId, userId, referenceTime);

            assertThat(findBracket(response, AgeBracket.OVER_THIRTY).count()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Terminal Status Filtering")
    class TerminalStatusFilteringTests {

        @BeforeEach
        void setUpAccess() {
            when(membershipRepository.findActiveByUserIdAndPropertyId(userId, propertyId))
                    .thenReturn(List.of(createMembership(Role.PROPERTY_MANAGER)));
        }

        @Test
        @DisplayName("should exclude CLOSED tickets from the report")
        void shouldExcludeClosedTickets() {
            Ticket closedTicket = createTicket(TicketStatus.CLOSED, referenceTime.minus(5, ChronoUnit.DAYS));
            when(ticketRepository.findByPropertyId(propertyId)).thenReturn(List.of(closedTicket));

            AgeingReportResponse response = ageingReportService.generateReport(propertyId, userId, referenceTime);

            assertThat(response.totalOpenTickets()).isZero();
        }

        @Test
        @DisplayName("should exclude CANCELLED tickets from the report")
        void shouldExcludeCancelledTickets() {
            Ticket cancelledTicket = createTicket(TicketStatus.CANCELLED, referenceTime.minus(2, ChronoUnit.DAYS));
            when(ticketRepository.findByPropertyId(propertyId)).thenReturn(List.of(cancelledTicket));

            AgeingReportResponse response = ageingReportService.generateReport(propertyId, userId, referenceTime);

            assertThat(response.totalOpenTickets()).isZero();
        }

        @Test
        @DisplayName("should exclude REJECTED tickets from the report")
        void shouldExcludeRejectedTickets() {
            Ticket rejectedTicket = createTicket(TicketStatus.REJECTED, referenceTime.minus(10, ChronoUnit.DAYS));
            when(ticketRepository.findByPropertyId(propertyId)).thenReturn(List.of(rejectedTicket));

            AgeingReportResponse response = ageingReportService.generateReport(propertyId, userId, referenceTime);

            assertThat(response.totalOpenTickets()).isZero();
        }

        @Test
        @DisplayName("should include RESOLVED tickets in the report (not terminal until CLOSED)")
        void shouldIncludeResolvedTickets() {
            Ticket resolvedTicket = createTicket(TicketStatus.RESOLVED, referenceTime.minus(5, ChronoUnit.DAYS));
            when(ticketRepository.findByPropertyId(propertyId)).thenReturn(List.of(resolvedTicket));

            AgeingReportResponse response = ageingReportService.generateReport(propertyId, userId, referenceTime);

            assertThat(response.totalOpenTickets()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Multiple Tickets")
    class MultipleTicketsTests {

        @BeforeEach
        void setUpAccess() {
            when(membershipRepository.findActiveByUserIdAndPropertyId(userId, propertyId))
                    .thenReturn(List.of(createMembership(Role.PROPERTY_MANAGER)));
        }

        @Test
        @DisplayName("should distribute multiple tickets across correct brackets")
        void shouldDistributeMultipleTickets() {
            Ticket ticket1 = createTicket(TicketStatus.SUBMITTED, referenceTime.minus(1, ChronoUnit.DAYS));
            Ticket ticket2 = createTicket(TicketStatus.ASSIGNED, referenceTime.minus(5, ChronoUnit.DAYS));
            Ticket ticket3 = createTicket(TicketStatus.IN_PROGRESS, referenceTime.minus(10, ChronoUnit.DAYS));
            Ticket ticket4 = createTicket(TicketStatus.IN_PROGRESS, referenceTime.minus(20, ChronoUnit.DAYS));
            Ticket ticket5 = createTicket(TicketStatus.SUBMITTED, referenceTime.minus(50, ChronoUnit.DAYS));

            when(ticketRepository.findByPropertyId(propertyId))
                    .thenReturn(List.of(ticket1, ticket2, ticket3, ticket4, ticket5));

            AgeingReportResponse response = ageingReportService.generateReport(propertyId, userId, referenceTime);

            assertThat(response.totalOpenTickets()).isEqualTo(5);
            assertThat(findBracket(response, AgeBracket.ZERO_TO_THREE).count()).isEqualTo(1);
            assertThat(findBracket(response, AgeBracket.FOUR_TO_SEVEN).count()).isEqualTo(1);
            assertThat(findBracket(response, AgeBracket.EIGHT_TO_FOURTEEN).count()).isEqualTo(1);
            assertThat(findBracket(response, AgeBracket.FIFTEEN_TO_THIRTY).count()).isEqualTo(1);
            assertThat(findBracket(response, AgeBracket.OVER_THIRTY).count()).isEqualTo(1);
        }

        @Test
        @DisplayName("should return all five brackets even when empty")
        void shouldReturnAllBracketsEvenWhenEmpty() {
            when(ticketRepository.findByPropertyId(propertyId)).thenReturn(List.of());

            AgeingReportResponse response = ageingReportService.generateReport(propertyId, userId, referenceTime);

            assertThat(response.brackets()).hasSize(5);
            assertThat(response.totalOpenTickets()).isZero();
            for (AgeingBracketEntry entry : response.brackets()) {
                assertThat(entry.count()).isZero();
                assertThat(entry.ticketIds()).isEmpty();
            }
        }

        @Test
        @DisplayName("should only include open tickets and exclude terminal ones")
        void shouldMixOpenAndTerminalTickets() {
            Ticket openTicket = createTicket(TicketStatus.IN_PROGRESS, referenceTime.minus(2, ChronoUnit.DAYS));
            Ticket closedTicket = createTicket(TicketStatus.CLOSED, referenceTime.minus(2, ChronoUnit.DAYS));
            Ticket cancelledTicket = createTicket(TicketStatus.CANCELLED, referenceTime.minus(6, ChronoUnit.DAYS));

            when(ticketRepository.findByPropertyId(propertyId))
                    .thenReturn(List.of(openTicket, closedTicket, cancelledTicket));

            AgeingReportResponse response = ageingReportService.generateReport(propertyId, userId, referenceTime);

            assertThat(response.totalOpenTickets()).isEqualTo(1);
            assertThat(findBracket(response, AgeBracket.ZERO_TO_THREE).count()).isEqualTo(1);
            assertThat(findBracket(response, AgeBracket.ZERO_TO_THREE).ticketIds()).contains(openTicket.getId());
        }
    }

    @Nested
    @DisplayName("Age Calculation")
    class AgeCalculationTests {

        @Test
        @DisplayName("should calculate age as 0 for same-instant creation")
        void shouldCalculateZeroDaysForSameInstant() {
            long days = AgeingReportService.calculateAgeDays(referenceTime, referenceTime);
            assertThat(days).isZero();
        }

        @Test
        @DisplayName("should calculate age as 0 for less than 24 hours")
        void shouldCalculateZeroDaysForLessThan24Hours() {
            Instant createdAt = referenceTime.minus(23, ChronoUnit.HOURS);
            long days = AgeingReportService.calculateAgeDays(createdAt, referenceTime);
            assertThat(days).isZero();
        }

        @Test
        @DisplayName("should calculate age as 1 for exactly 24 hours")
        void shouldCalculateOneDayForExactly24Hours() {
            Instant createdAt = referenceTime.minus(24, ChronoUnit.HOURS);
            long days = AgeingReportService.calculateAgeDays(createdAt, referenceTime);
            assertThat(days).isEqualTo(1);
        }

        @Test
        @DisplayName("should calculate age as 3 for exactly 3 days")
        void shouldCalculateThreeDaysCorrectly() {
            Instant createdAt = referenceTime.minus(3, ChronoUnit.DAYS);
            long days = AgeingReportService.calculateAgeDays(createdAt, referenceTime);
            assertThat(days).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("AgeBracket.fromDays")
    class AgeBracketFromDaysTests {

        @Test
        @DisplayName("should throw for negative days")
        void shouldThrowForNegativeDays() {
            assertThatThrownBy(() -> AgeBracket.fromDays(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("negative");
        }

        @Test
        @DisplayName("boundary: 0 days -> ZERO_TO_THREE")
        void zeroDays() {
            assertThat(AgeBracket.fromDays(0)).isEqualTo(AgeBracket.ZERO_TO_THREE);
        }

        @Test
        @DisplayName("boundary: 3 days -> ZERO_TO_THREE")
        void threeDays() {
            assertThat(AgeBracket.fromDays(3)).isEqualTo(AgeBracket.ZERO_TO_THREE);
        }

        @Test
        @DisplayName("boundary: 4 days -> FOUR_TO_SEVEN")
        void fourDays() {
            assertThat(AgeBracket.fromDays(4)).isEqualTo(AgeBracket.FOUR_TO_SEVEN);
        }

        @Test
        @DisplayName("boundary: 7 days -> FOUR_TO_SEVEN")
        void sevenDays() {
            assertThat(AgeBracket.fromDays(7)).isEqualTo(AgeBracket.FOUR_TO_SEVEN);
        }

        @Test
        @DisplayName("boundary: 8 days -> EIGHT_TO_FOURTEEN")
        void eightDays() {
            assertThat(AgeBracket.fromDays(8)).isEqualTo(AgeBracket.EIGHT_TO_FOURTEEN);
        }

        @Test
        @DisplayName("boundary: 14 days -> EIGHT_TO_FOURTEEN")
        void fourteenDays() {
            assertThat(AgeBracket.fromDays(14)).isEqualTo(AgeBracket.EIGHT_TO_FOURTEEN);
        }

        @Test
        @DisplayName("boundary: 15 days -> FIFTEEN_TO_THIRTY")
        void fifteenDays() {
            assertThat(AgeBracket.fromDays(15)).isEqualTo(AgeBracket.FIFTEEN_TO_THIRTY);
        }

        @Test
        @DisplayName("boundary: 30 days -> FIFTEEN_TO_THIRTY")
        void thirtyDays() {
            assertThat(AgeBracket.fromDays(30)).isEqualTo(AgeBracket.FIFTEEN_TO_THIRTY);
        }

        @Test
        @DisplayName("boundary: 31 days -> OVER_THIRTY")
        void thirtyOneDays() {
            assertThat(AgeBracket.fromDays(31)).isEqualTo(AgeBracket.OVER_THIRTY);
        }

        @Test
        @DisplayName("boundary: 365 days -> OVER_THIRTY")
        void veryOld() {
            assertThat(AgeBracket.fromDays(365)).isEqualTo(AgeBracket.OVER_THIRTY);
        }
    }

    private AgeingBracketEntry findBracket(AgeingReportResponse response, AgeBracket bracket) {
        return response.brackets().stream()
                .filter(entry -> entry.bracket() == bracket)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Bracket " + bracket + " not found in response"));
    }
}
