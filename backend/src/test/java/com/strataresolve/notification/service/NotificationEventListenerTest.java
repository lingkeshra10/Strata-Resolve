package com.strataresolve.notification.service;

import com.strataresolve.notification.domain.Notification;
import com.strataresolve.shared.event.AssignmentCreatedEvent;
import com.strataresolve.shared.event.StatusChangedEvent;
import com.strataresolve.shared.event.TicketCreatedEvent;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationEventListener")
class NotificationEventListenerTest {

    @Mock
    private NotificationOutboxService outboxService;

    @Mock
    private MembershipRepository membershipRepository;

    private NotificationEventListener eventListener;

    private UUID propertyId;
    private UUID actingUserId;
    private UUID ticketId;
    private UUID propertyManagerId1;
    private UUID propertyManagerId2;

    @BeforeEach
    void setUp() {
        eventListener = new NotificationEventListener(outboxService, membershipRepository);

        propertyId = UUID.randomUUID();
        actingUserId = UUID.randomUUID();
        ticketId = UUID.randomUUID();
        propertyManagerId1 = UUID.randomUUID();
        propertyManagerId2 = UUID.randomUUID();
    }

    private Membership createMembership(UUID userId, Role role) {
        Membership membership = Membership.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .role(role)
                .isActive(true)
                .build();
        membership.setPropertyId(propertyId);
        return membership;
    }

    @Nested
    @DisplayName("onTicketCreated")
    class OnTicketCreatedTests {

        @Test
        @DisplayName("should notify all property managers when ticket is created")
        void shouldNotifyAllPropertyManagers() {
            TicketCreatedEvent event = new TicketCreatedEvent(
                    actingUserId, propertyId, ticketId,
                    UUID.randomUUID(), "SR-2025-000001", "PLUMBING", "HIGH");

            when(membershipRepository.findActiveByPropertyId(propertyId))
                    .thenReturn(List.of(
                            createMembership(propertyManagerId1, Role.PROPERTY_MANAGER),
                            createMembership(propertyManagerId2, Role.PROPERTY_MANAGER)
                    ));

            when(outboxService.createNotification(any(), any(), any(), any(), any(), any()))
                    .thenReturn(Notification.builder().build());

            eventListener.onTicketCreated(event);

            verify(outboxService).createNotification(
                    eq(propertyId), eq(propertyManagerId1), eq(ticketId),
                    eq("TICKET_CREATED"), any(String.class), any(String.class));
            verify(outboxService).createNotification(
                    eq(propertyId), eq(propertyManagerId2), eq(ticketId),
                    eq("TICKET_CREATED"), any(String.class), any(String.class));
        }

        @Test
        @DisplayName("should not notify the acting user even if they are a property manager")
        void shouldNotNotifyActingUser() {
            TicketCreatedEvent event = new TicketCreatedEvent(
                    propertyManagerId1, propertyId, ticketId,
                    UUID.randomUUID(), "SR-2025-000001", "PLUMBING", "HIGH");

            when(membershipRepository.findActiveByPropertyId(propertyId))
                    .thenReturn(List.of(
                            createMembership(propertyManagerId1, Role.PROPERTY_MANAGER),
                            createMembership(propertyManagerId2, Role.PROPERTY_MANAGER)
                    ));

            when(outboxService.createNotification(any(), any(), any(), any(), any(), any()))
                    .thenReturn(Notification.builder().build());

            eventListener.onTicketCreated(event);

            // Should only notify propertyManagerId2, not the acting user (propertyManagerId1)
            verify(outboxService, never()).createNotification(
                    any(), eq(propertyManagerId1), any(), any(), any(), any());
            verify(outboxService).createNotification(
                    eq(propertyId), eq(propertyManagerId2), eq(ticketId),
                    eq("TICKET_CREATED"), any(String.class), any(String.class));
        }

        @Test
        @DisplayName("should not create notifications when no property managers exist")
        void shouldNotCreateNotificationsWhenNoManagers() {
            TicketCreatedEvent event = new TicketCreatedEvent(
                    actingUserId, propertyId, ticketId,
                    UUID.randomUUID(), "SR-2025-000001", "PLUMBING", "HIGH");

            when(membershipRepository.findActiveByPropertyId(propertyId))
                    .thenReturn(List.of(
                            createMembership(UUID.randomUUID(), Role.RESIDENT_OWNER)
                    ));

            eventListener.onTicketCreated(event);

            verify(outboxService, never()).createNotification(
                    any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("should filter out non-property-manager roles")
        void shouldFilterNonManagerRoles() {
            TicketCreatedEvent event = new TicketCreatedEvent(
                    actingUserId, propertyId, ticketId,
                    UUID.randomUUID(), "SR-2025-000001", "PLUMBING", "HIGH");

            when(membershipRepository.findActiveByPropertyId(propertyId))
                    .thenReturn(List.of(
                            createMembership(propertyManagerId1, Role.PROPERTY_MANAGER),
                            createMembership(UUID.randomUUID(), Role.RESIDENT_OWNER),
                            createMembership(UUID.randomUUID(), Role.TECHNICIAN)
                    ));

            when(outboxService.createNotification(any(), any(), any(), any(), any(), any()))
                    .thenReturn(Notification.builder().build());

            eventListener.onTicketCreated(event);

            verify(outboxService, times(1)).createNotification(
                    any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("onStatusChanged")
    class OnStatusChangedTests {

        @Test
        @DisplayName("should notify property managers about status changes")
        void shouldNotifyPropertyManagers() {
            StatusChangedEvent event = new StatusChangedEvent(
                    actingUserId, propertyId, ticketId,
                    "SUBMITTED", "ACKNOWLEDGED", null);

            when(membershipRepository.findActiveByPropertyId(propertyId))
                    .thenReturn(List.of(
                            createMembership(propertyManagerId1, Role.PROPERTY_MANAGER)
                    ));

            when(outboxService.createNotification(any(), any(), any(), any(), any(), any()))
                    .thenReturn(Notification.builder().build());

            eventListener.onStatusChanged(event);

            verify(outboxService).createNotification(
                    eq(propertyId), eq(propertyManagerId1), eq(ticketId),
                    eq("STATUS_CHANGED"), any(String.class), any(String.class));
        }

        @Test
        @DisplayName("should not notify the acting user for status changes")
        void shouldNotNotifyActingUserForStatusChange() {
            StatusChangedEvent event = new StatusChangedEvent(
                    propertyManagerId1, propertyId, ticketId,
                    "SUBMITTED", "ACKNOWLEDGED", null);

            when(membershipRepository.findActiveByPropertyId(propertyId))
                    .thenReturn(List.of(
                            createMembership(propertyManagerId1, Role.PROPERTY_MANAGER),
                            createMembership(propertyManagerId2, Role.PROPERTY_MANAGER)
                    ));

            when(outboxService.createNotification(any(), any(), any(), any(), any(), any()))
                    .thenReturn(Notification.builder().build());

            eventListener.onStatusChanged(event);

            verify(outboxService, never()).createNotification(
                    any(), eq(propertyManagerId1), any(), any(), any(), any());
            verify(outboxService).createNotification(
                    eq(propertyId), eq(propertyManagerId2), eq(ticketId),
                    eq("STATUS_CHANGED"), any(String.class), any(String.class));
        }

        @Test
        @DisplayName("should include reason in notification body when present")
        void shouldIncludeReasonInBody() {
            StatusChangedEvent event = new StatusChangedEvent(
                    actingUserId, propertyId, ticketId,
                    "RESOLVED", "REOPENED", "Issue not actually fixed");

            when(membershipRepository.findActiveByPropertyId(propertyId))
                    .thenReturn(List.of(
                            createMembership(propertyManagerId1, Role.PROPERTY_MANAGER)
                    ));

            when(outboxService.createNotification(any(), any(), any(), any(), any(), any()))
                    .thenReturn(Notification.builder().build());

            eventListener.onStatusChanged(event);

            verify(outboxService).createNotification(
                    eq(propertyId), eq(propertyManagerId1), eq(ticketId),
                    eq("STATUS_CHANGED"), any(String.class),
                    org.mockito.ArgumentMatchers.contains("Issue not actually fixed"));
        }
    }

    @Nested
    @DisplayName("onAssignmentCreated")
    class OnAssignmentCreatedTests {

        @Test
        @DisplayName("should notify the assignee about new assignment")
        void shouldNotifyAssignee() {
            UUID assigneeId = UUID.randomUUID();
            AssignmentCreatedEvent event = new AssignmentCreatedEvent(
                    actingUserId, propertyId, ticketId, assigneeId, "TECHNICIAN");

            when(outboxService.createNotification(any(), any(), any(), any(), any(), any()))
                    .thenReturn(Notification.builder().build());

            eventListener.onAssignmentCreated(event);

            verify(outboxService).createNotification(
                    eq(propertyId), eq(assigneeId), eq(ticketId),
                    eq("ASSIGNMENT_CREATED"), any(String.class), any(String.class));
        }

        @Test
        @DisplayName("should include assignment type in notification body")
        void shouldIncludeAssignmentTypeInBody() {
            UUID assigneeId = UUID.randomUUID();
            AssignmentCreatedEvent event = new AssignmentCreatedEvent(
                    actingUserId, propertyId, ticketId, assigneeId, "VENDOR");

            when(outboxService.createNotification(any(), any(), any(), any(), any(), any()))
                    .thenReturn(Notification.builder().build());

            eventListener.onAssignmentCreated(event);

            verify(outboxService).createNotification(
                    eq(propertyId), eq(assigneeId), eq(ticketId),
                    eq("ASSIGNMENT_CREATED"), any(String.class),
                    org.mockito.ArgumentMatchers.contains("VENDOR"));
        }
    }
}
