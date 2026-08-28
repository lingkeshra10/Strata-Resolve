package com.strataresolve.notification.service;

import com.strataresolve.common.event.AssignmentCreatedEvent;
import com.strataresolve.common.event.StatusChangedEvent;
import com.strataresolve.common.event.TicketCreatedEvent;
import com.strataresolve.notification.client.IdentityServiceClient;
import com.strataresolve.notification.model.NotificationRecipient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationEventListener")
class NotificationEventListenerTest {

    @Mock
    private NotificationOutboxService outboxService;

    @Mock
    private IdentityServiceClient identityClient;

    private NotificationEventListener eventListener;

    private UUID propertyId;
    private UUID actingUserId;
    private UUID ticketId;
    private UUID propertyManagerId1;
    private UUID propertyManagerId2;

    private NotificationRecipient manager1;
    private NotificationRecipient manager2;

    @BeforeEach
    void setUp() {

        eventListener = new NotificationEventListener(
                outboxService,
                identityClient
        );

        propertyId = UUID.randomUUID();
        actingUserId = UUID.randomUUID();
        ticketId = UUID.randomUUID();

        propertyManagerId1 = UUID.randomUUID();
        propertyManagerId2 = UUID.randomUUID();

        manager1 = new NotificationRecipient(
                propertyManagerId1,
                "manager1@example.com"
        );

        manager2 = new NotificationRecipient(
                propertyManagerId2,
                "manager2@example.com"
        );
    }


    // =========================================================
    // Ticket Created
    // =========================================================

    @Nested
    @DisplayName("onTicketCreated")
    class OnTicketCreatedTests {

        @Test
        @DisplayName("should notify all property managers when ticket is created")
        void shouldNotifyAllPropertyManagers() {

            TicketCreatedEvent event = new TicketCreatedEvent(
                    actingUserId,
                    propertyId,
                    ticketId,
                    UUID.randomUUID(),
                    "SR-2025-000001",
                    "PLUMBING",
                    "HIGH"
            );

            when(identityClient.findPropertyManagers(propertyId))
                    .thenReturn(List.of(
                            manager1,
                            manager2
                    ));

            eventListener.onTicketCreated(event);

            verify(outboxService).createNotification(
                    eq(propertyId),
                    eq(propertyManagerId1),
                    eq("manager1@example.com"),
                    eq(ticketId),
                    eq("TICKET_CREATED"),
                    any(String.class),
                    any(String.class)
            );

            verify(outboxService).createNotification(
                    eq(propertyId),
                    eq(propertyManagerId2),
                    eq("manager2@example.com"),
                    eq(ticketId),
                    eq("TICKET_CREATED"),
                    any(String.class),
                    any(String.class)
            );

            verify(identityClient).findPropertyManagers(propertyId);
        }

        @Test
        @DisplayName("should not notify acting user when acting user is a property manager")
        void shouldNotNotifyActingUser() {

            NotificationRecipient actingManager =
                    new NotificationRecipient(
                            propertyManagerId1,
                            "acting-manager@example.com"
                    );

            TicketCreatedEvent event = new TicketCreatedEvent(
                    propertyManagerId1,
                    propertyId,
                    ticketId,
                    UUID.randomUUID(),
                    "SR-2025-000001",
                    "PLUMBING",
                    "HIGH"
            );

            when(identityClient.findPropertyManagers(propertyId))
                    .thenReturn(List.of(
                            actingManager,
                            manager2
                    ));

            eventListener.onTicketCreated(event);

            verify(outboxService, never())
                    .createNotification(
                            any(),
                            eq(propertyManagerId1),
                            any(),
                            any(),
                            any(),
                            any(),
                            any()
                    );

            verify(outboxService).createNotification(
                    eq(propertyId),
                    eq(propertyManagerId2),
                    eq("manager2@example.com"),
                    eq(ticketId),
                    eq("TICKET_CREATED"),
                    any(String.class),
                    any(String.class)
            );
        }

        @Test
        @DisplayName("should not create notifications when no property managers exist")
        void shouldNotCreateNotificationsWhenNoManagers() {

            TicketCreatedEvent event = new TicketCreatedEvent(
                    actingUserId,
                    propertyId,
                    ticketId,
                    UUID.randomUUID(),
                    "SR-2025-000001",
                    "PLUMBING",
                    "HIGH"
            );

            when(identityClient.findPropertyManagers(propertyId))
                    .thenReturn(List.of());

            eventListener.onTicketCreated(event);

            verify(identityClient)
                    .findPropertyManagers(propertyId);

            verifyNoInteractions(outboxService);
        }

        @Test
        @DisplayName("should use property managers returned by identity service")
        void shouldUsePropertyManagersReturnedByIdentityService() {

            TicketCreatedEvent event = new TicketCreatedEvent(
                    actingUserId,
                    propertyId,
                    ticketId,
                    UUID.randomUUID(),
                    "SR-2025-000001",
                    "PLUMBING",
                    "HIGH"
            );

            when(identityClient.findPropertyManagers(propertyId))
                    .thenReturn(List.of(manager1));

            eventListener.onTicketCreated(event);

            verify(identityClient, times(1))
                    .findPropertyManagers(propertyId);

            verify(outboxService, times(1))
                    .createNotification(
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            any()
                    );
        }
    }


    // =========================================================
    // Status Changed
    // =========================================================

    @Nested
    @DisplayName("onStatusChanged")
    class OnStatusChangedTests {

        @Test
        @DisplayName("should notify property managers about status changes")
        void shouldNotifyPropertyManagers() {

            StatusChangedEvent event = new StatusChangedEvent(
                    actingUserId,
                    propertyId,
                    ticketId,
                    "SUBMITTED",
                    "ACKNOWLEDGED",
                    null
            );

            when(identityClient.findPropertyManagers(propertyId))
                    .thenReturn(List.of(manager1));

            eventListener.onStatusChanged(event);

            verify(outboxService).createNotification(
                    eq(propertyId),
                    eq(propertyManagerId1),
                    eq("manager1@example.com"),
                    eq(ticketId),
                    eq("STATUS_CHANGED"),
                    any(String.class),
                    any(String.class)
            );
        }

        @Test
        @DisplayName("should not notify acting user for status changes")
        void shouldNotNotifyActingUserForStatusChange() {

            NotificationRecipient actingManager =
                    new NotificationRecipient(
                            propertyManagerId1,
                            "acting-manager@example.com"
                    );

            StatusChangedEvent event = new StatusChangedEvent(
                    propertyManagerId1,
                    propertyId,
                    ticketId,
                    "SUBMITTED",
                    "ACKNOWLEDGED",
                    null
            );

            when(identityClient.findPropertyManagers(propertyId))
                    .thenReturn(List.of(
                            actingManager,
                            manager2
                    ));

            eventListener.onStatusChanged(event);

            verify(outboxService, never())
                    .createNotification(
                            any(),
                            eq(propertyManagerId1),
                            any(),
                            any(),
                            any(),
                            any(),
                            any()
                    );

            verify(outboxService).createNotification(
                    eq(propertyId),
                    eq(propertyManagerId2),
                    eq("manager2@example.com"),
                    eq(ticketId),
                    eq("STATUS_CHANGED"),
                    any(String.class),
                    any(String.class)
            );
        }

        @Test
        @DisplayName("should include reason in notification body when present")
        void shouldIncludeReasonInBody() {

            StatusChangedEvent event = new StatusChangedEvent(
                    actingUserId,
                    propertyId,
                    ticketId,
                    "RESOLVED",
                    "REOPENED",
                    "Issue not actually fixed"
            );

            when(identityClient.findPropertyManagers(propertyId))
                    .thenReturn(List.of(manager1));

            eventListener.onStatusChanged(event);

            verify(outboxService).createNotification(
                    eq(propertyId),
                    eq(propertyManagerId1),
                    eq("manager1@example.com"),
                    eq(ticketId),
                    eq("STATUS_CHANGED"),
                    any(String.class),
                    contains("Issue not actually fixed")
            );
        }

        @Test
        @DisplayName("should not create notification when no property managers exist")
        void shouldNotCreateNotificationWhenNoManagersExist() {

            StatusChangedEvent event = new StatusChangedEvent(
                    actingUserId,
                    propertyId,
                    ticketId,
                    "SUBMITTED",
                    "ACKNOWLEDGED",
                    null
            );

            when(identityClient.findPropertyManagers(propertyId))
                    .thenReturn(List.of());

            eventListener.onStatusChanged(event);

            verifyNoInteractions(outboxService);
        }
    }


    // =========================================================
    // Assignment Created
    // =========================================================

    @Nested
    @DisplayName("onAssignmentCreated")
    class OnAssignmentCreatedTests {

        @Test
        @DisplayName("should notify assignee about new assignment")
        void shouldNotifyAssignee() {

            UUID assigneeId = UUID.randomUUID();

            NotificationRecipient recipient =
                    new NotificationRecipient(
                            assigneeId,
                            "assignee@example.com"
                    );

            AssignmentCreatedEvent event =
                    new AssignmentCreatedEvent(
                            actingUserId,
                            propertyId,
                            ticketId,
                            assigneeId,
                            "TECHNICIAN"
                    );

            when(identityClient.findUser(assigneeId))
                    .thenReturn(Optional.of(recipient));

            eventListener.onAssignmentCreated(event);

            verify(identityClient)
                    .findUser(assigneeId);

            verify(outboxService).createNotification(
                    eq(propertyId),
                    eq(assigneeId),
                    eq("assignee@example.com"),
                    eq(ticketId),
                    eq("ASSIGNMENT_CREATED"),
                    any(String.class),
                    any(String.class)
            );
        }

        @Test
        @DisplayName("should include assignment type in notification body")
        void shouldIncludeAssignmentTypeInBody() {

            UUID assigneeId = UUID.randomUUID();

            NotificationRecipient recipient =
                    new NotificationRecipient(
                            assigneeId,
                            "vendor@example.com"
                    );

            AssignmentCreatedEvent event =
                    new AssignmentCreatedEvent(
                            actingUserId,
                            propertyId,
                            ticketId,
                            assigneeId,
                            "VENDOR"
                    );

            when(identityClient.findUser(assigneeId))
                    .thenReturn(Optional.of(recipient));

            eventListener.onAssignmentCreated(event);

            verify(outboxService).createNotification(
                    eq(propertyId),
                    eq(assigneeId),
                    eq("vendor@example.com"),
                    eq(ticketId),
                    eq("ASSIGNMENT_CREATED"),
                    any(String.class),
                    contains("VENDOR")
            );
        }

        @Test
        @DisplayName("should not create notification when assignee cannot be found")
        void shouldNotCreateNotificationWhenAssigneeNotFound() {

            UUID assigneeId = UUID.randomUUID();

            AssignmentCreatedEvent event =
                    new AssignmentCreatedEvent(
                            actingUserId,
                            propertyId,
                            ticketId,
                            assigneeId,
                            "TECHNICIAN"
                    );

            when(identityClient.findUser(assigneeId))
                    .thenReturn(Optional.empty());

            eventListener.onAssignmentCreated(event);

            verify(identityClient)
                    .findUser(assigneeId);

            verifyNoInteractions(outboxService);
        }
    }
}