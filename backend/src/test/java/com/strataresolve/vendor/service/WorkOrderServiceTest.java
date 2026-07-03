package com.strataresolve.vendor.service;

import com.strataresolve.shared.event.DomainEventPublisher;
import com.strataresolve.shared.event.WorkOrderAttachmentUploadedEvent;
import com.strataresolve.shared.exception.BusinessRuleViolationException;
import com.strataresolve.shared.exception.InvalidTransitionException;
import com.strataresolve.shared.exception.ResourceNotFoundException;
import com.strataresolve.shared.filestorage.FileMetadata;
import com.strataresolve.shared.filestorage.FileReference;
import com.strataresolve.shared.filestorage.FileStorageService;
import com.strataresolve.ticket.domain.Attachment;
import com.strataresolve.ticket.repository.AttachmentRepository;
import com.strataresolve.user.domain.Membership;
import com.strataresolve.user.domain.Role;
import com.strataresolve.user.repository.MembershipRepository;
import com.strataresolve.vendor.domain.Vendor;
import com.strataresolve.vendor.domain.WorkOrder;
import com.strataresolve.vendor.domain.WorkOrderStatus;
import com.strataresolve.vendor.repository.VendorRepository;
import com.strataresolve.vendor.repository.WorkOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkOrderServiceTest {

    @Mock
    private WorkOrderRepository workOrderRepository;

    @Mock
    private VendorRepository vendorRepository;

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private DomainEventPublisher eventPublisher;

    private WorkOrderService workOrderService;

    private static final UUID PROPERTY_ID = UUID.randomUUID();
    private static final UUID VENDOR_ID = UUID.randomUUID();
    private static final UUID TICKET_ID = UUID.randomUUID();
    private static final UUID WORK_ORDER_ID = UUID.randomUUID();
    private static final UUID ACTING_USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        workOrderService = new WorkOrderService(
                workOrderRepository, vendorRepository, membershipRepository,
                attachmentRepository, fileStorageService, eventPublisher
        );
    }

    // --- create tests ---

    @Nested
    class CreateTests {

        @Test
        void create_withValidRequest_createsWorkOrder() {
            Vendor vendor = createActiveVendor();
            when(vendorRepository.findByIdAndPropertyId(VENDOR_ID, PROPERTY_ID))
                    .thenReturn(Optional.of(vendor));
            when(workOrderRepository.existsByTicketIdAndPropertyId(TICKET_ID, PROPERTY_ID))
                    .thenReturn(false);
            when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(invocation -> {
                WorkOrder wo = invocation.getArgument(0);
                wo.setId(WORK_ORDER_ID);
                return wo;
            });

            WorkOrder result = workOrderService.create(TICKET_ID, VENDOR_ID, PROPERTY_ID, ACTING_USER_ID);

            assertThat(result.getTicketId()).isEqualTo(TICKET_ID);
            assertThat(result.getVendorId()).isEqualTo(VENDOR_ID);
            assertThat(result.getPropertyId()).isEqualTo(PROPERTY_ID);
            assertThat(result.getStatus()).isEqualTo(WorkOrderStatus.CREATED);
        }

        @Test
        void create_withNonExistentVendor_throwsException() {
            when(vendorRepository.findByIdAndPropertyId(VENDOR_ID, PROPERTY_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> workOrderService.create(TICKET_ID, VENDOR_ID, PROPERTY_ID, ACTING_USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Vendor");

            verify(workOrderRepository, never()).save(any());
        }

        @Test
        void create_withInactiveVendor_throwsException() {
            Vendor vendor = createInactiveVendor();
            when(vendorRepository.findByIdAndPropertyId(VENDOR_ID, PROPERTY_ID))
                    .thenReturn(Optional.of(vendor));

            assertThatThrownBy(() -> workOrderService.create(TICKET_ID, VENDOR_ID, PROPERTY_ID, ACTING_USER_ID))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("inactive");

            verify(workOrderRepository, never()).save(any());
        }

        @Test
        void create_withExistingWorkOrderForTicket_throwsException() {
            Vendor vendor = createActiveVendor();
            when(vendorRepository.findByIdAndPropertyId(VENDOR_ID, PROPERTY_ID))
                    .thenReturn(Optional.of(vendor));
            when(workOrderRepository.existsByTicketIdAndPropertyId(TICKET_ID, PROPERTY_ID))
                    .thenReturn(true);

            assertThatThrownBy(() -> workOrderService.create(TICKET_ID, VENDOR_ID, PROPERTY_ID, ACTING_USER_ID))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("already exists");

            verify(workOrderRepository, never()).save(any());
        }
    }

    // --- accept tests ---

    @Nested
    class AcceptTests {

        @Test
        void accept_fromCreatedStatus_succeeds() {
            WorkOrder workOrder = createWorkOrder(WorkOrderStatus.CREATED);
            when(workOrderRepository.findByIdAndPropertyId(WORK_ORDER_ID, PROPERTY_ID))
                    .thenReturn(Optional.of(workOrder));
            when(workOrderRepository.save(any(WorkOrder.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            WorkOrder result = workOrderService.accept(WORK_ORDER_ID, PROPERTY_ID, ACTING_USER_ID);

            assertThat(result.getStatus()).isEqualTo(WorkOrderStatus.ACCEPTED);
        }

        @Test
        void accept_fromCompletedStatus_throwsException() {
            WorkOrder workOrder = createWorkOrder(WorkOrderStatus.COMPLETED);
            when(workOrderRepository.findByIdAndPropertyId(WORK_ORDER_ID, PROPERTY_ID))
                    .thenReturn(Optional.of(workOrder));

            assertThatThrownBy(() -> workOrderService.accept(WORK_ORDER_ID, PROPERTY_ID, ACTING_USER_ID))
                    .isInstanceOf(InvalidTransitionException.class);

            verify(workOrderRepository, never()).save(any());
        }

        @Test
        void accept_withNonExistentWorkOrder_throwsException() {
            when(workOrderRepository.findByIdAndPropertyId(WORK_ORDER_ID, PROPERTY_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> workOrderService.accept(WORK_ORDER_ID, PROPERTY_ID, ACTING_USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("WorkOrder");
        }
    }

    // --- startWork tests ---

    @Nested
    class StartWorkTests {

        @Test
        void startWork_fromAcceptedStatus_succeeds() {
            WorkOrder workOrder = createWorkOrder(WorkOrderStatus.ACCEPTED);
            when(workOrderRepository.findByIdAndPropertyId(WORK_ORDER_ID, PROPERTY_ID))
                    .thenReturn(Optional.of(workOrder));
            when(workOrderRepository.save(any(WorkOrder.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            WorkOrder result = workOrderService.startWork(WORK_ORDER_ID, PROPERTY_ID, ACTING_USER_ID);

            assertThat(result.getStatus()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
        }

        @Test
        void startWork_fromCreatedStatus_throwsException() {
            WorkOrder workOrder = createWorkOrder(WorkOrderStatus.CREATED);
            when(workOrderRepository.findByIdAndPropertyId(WORK_ORDER_ID, PROPERTY_ID))
                    .thenReturn(Optional.of(workOrder));

            assertThatThrownBy(() -> workOrderService.startWork(WORK_ORDER_ID, PROPERTY_ID, ACTING_USER_ID))
                    .isInstanceOf(InvalidTransitionException.class);

            verify(workOrderRepository, never()).save(any());
        }
    }

    // --- complete tests ---

    @Nested
    class CompleteTests {

        @Test
        void complete_fromInProgressStatus_succeeds() {
            WorkOrder workOrder = createWorkOrder(WorkOrderStatus.IN_PROGRESS);
            when(workOrderRepository.findByIdAndPropertyId(WORK_ORDER_ID, PROPERTY_ID))
                    .thenReturn(Optional.of(workOrder));
            when(workOrderRepository.save(any(WorkOrder.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            WorkOrder result = workOrderService.complete(WORK_ORDER_ID, PROPERTY_ID, ACTING_USER_ID);

            assertThat(result.getStatus()).isEqualTo(WorkOrderStatus.COMPLETED);
            assertThat(result.getCompletedAt()).isNotNull();
        }

        @Test
        void complete_fromCreatedStatus_throwsException() {
            WorkOrder workOrder = createWorkOrder(WorkOrderStatus.CREATED);
            when(workOrderRepository.findByIdAndPropertyId(WORK_ORDER_ID, PROPERTY_ID))
                    .thenReturn(Optional.of(workOrder));

            assertThatThrownBy(() -> workOrderService.complete(WORK_ORDER_ID, PROPERTY_ID, ACTING_USER_ID))
                    .isInstanceOf(InvalidTransitionException.class);

            verify(workOrderRepository, never()).save(any());
        }
    }

    // --- cancel tests ---

    @Nested
    class CancelTests {

        @Test
        void cancel_fromCreatedStatus_succeeds() {
            WorkOrder workOrder = createWorkOrder(WorkOrderStatus.CREATED);
            when(workOrderRepository.findByIdAndPropertyId(WORK_ORDER_ID, PROPERTY_ID))
                    .thenReturn(Optional.of(workOrder));
            when(workOrderRepository.save(any(WorkOrder.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            WorkOrder result = workOrderService.cancel(WORK_ORDER_ID, PROPERTY_ID, ACTING_USER_ID);

            assertThat(result.getStatus()).isEqualTo(WorkOrderStatus.CANCELLED);
        }

        @Test
        void cancel_fromAcceptedStatus_succeeds() {
            WorkOrder workOrder = createWorkOrder(WorkOrderStatus.ACCEPTED);
            when(workOrderRepository.findByIdAndPropertyId(WORK_ORDER_ID, PROPERTY_ID))
                    .thenReturn(Optional.of(workOrder));
            when(workOrderRepository.save(any(WorkOrder.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            WorkOrder result = workOrderService.cancel(WORK_ORDER_ID, PROPERTY_ID, ACTING_USER_ID);

            assertThat(result.getStatus()).isEqualTo(WorkOrderStatus.CANCELLED);
        }

        @Test
        void cancel_fromInProgressStatus_succeeds() {
            WorkOrder workOrder = createWorkOrder(WorkOrderStatus.IN_PROGRESS);
            when(workOrderRepository.findByIdAndPropertyId(WORK_ORDER_ID, PROPERTY_ID))
                    .thenReturn(Optional.of(workOrder));
            when(workOrderRepository.save(any(WorkOrder.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            WorkOrder result = workOrderService.cancel(WORK_ORDER_ID, PROPERTY_ID, ACTING_USER_ID);

            assertThat(result.getStatus()).isEqualTo(WorkOrderStatus.CANCELLED);
        }

        @Test
        void cancel_fromCompletedStatus_throwsException() {
            WorkOrder workOrder = createWorkOrder(WorkOrderStatus.COMPLETED);
            when(workOrderRepository.findByIdAndPropertyId(WORK_ORDER_ID, PROPERTY_ID))
                    .thenReturn(Optional.of(workOrder));

            assertThatThrownBy(() -> workOrderService.cancel(WORK_ORDER_ID, PROPERTY_ID, ACTING_USER_ID))
                    .isInstanceOf(InvalidTransitionException.class);

            verify(workOrderRepository, never()).save(any());
        }

        @Test
        void cancel_fromCancelledStatus_throwsException() {
            WorkOrder workOrder = createWorkOrder(WorkOrderStatus.CANCELLED);
            when(workOrderRepository.findByIdAndPropertyId(WORK_ORDER_ID, PROPERTY_ID))
                    .thenReturn(Optional.of(workOrder));

            assertThatThrownBy(() -> workOrderService.cancel(WORK_ORDER_ID, PROPERTY_ID, ACTING_USER_ID))
                    .isInstanceOf(InvalidTransitionException.class);

            verify(workOrderRepository, never()).save(any());
        }
    }

    // --- findByVendorTechnician tests ---

    @Nested
    class FindByVendorTechnicianTests {

        @Test
        void findByVendorTechnician_withValidMembership_returnsWorkOrders() {
            Membership membership = Membership.builder()
                    .userId(ACTING_USER_ID)
                    .role(Role.VENDOR_TECHNICIAN)
                    .vendorId(VENDOR_ID)
                    .isActive(true)
                    .build();
            membership.setPropertyId(PROPERTY_ID);

            WorkOrder wo1 = createWorkOrder(WorkOrderStatus.CREATED);
            WorkOrder wo2 = createWorkOrder(WorkOrderStatus.ACCEPTED);

            when(membershipRepository.findActiveByUserIdAndPropertyId(ACTING_USER_ID, PROPERTY_ID))
                    .thenReturn(List.of(membership));
            when(workOrderRepository.findAllByVendorForTechnicians(VENDOR_ID, PROPERTY_ID))
                    .thenReturn(List.of(wo1, wo2));

            List<WorkOrder> result = workOrderService.findByVendorTechnician(ACTING_USER_ID, PROPERTY_ID);

            assertThat(result).hasSize(2);
        }

        @Test
        void findByVendorTechnician_withNoVendorMembership_throwsException() {
            Membership membership = Membership.builder()
                    .userId(ACTING_USER_ID)
                    .role(Role.RESIDENT_OWNER)
                    .isActive(true)
                    .build();
            membership.setPropertyId(PROPERTY_ID);

            when(membershipRepository.findActiveByUserIdAndPropertyId(ACTING_USER_ID, PROPERTY_ID))
                    .thenReturn(List.of(membership));

            assertThatThrownBy(() -> workOrderService.findByVendorTechnician(ACTING_USER_ID, PROPERTY_ID))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("vendor membership");
        }
    }

    // --- uploadEvidence tests ---

    @Nested
    class UploadEvidenceTests {

        @Test
        void uploadEvidence_withValidFile_storesAndPublishesEvent() throws IOException {
            WorkOrder workOrder = createWorkOrder(WorkOrderStatus.IN_PROGRESS);
            when(workOrderRepository.findByIdAndPropertyId(WORK_ORDER_ID, PROPERTY_ID))
                    .thenReturn(Optional.of(workOrder));

            MultipartFile file = mock(MultipartFile.class);
            when(file.getOriginalFilename()).thenReturn("quotation.pdf");
            when(file.getContentType()).thenReturn("application/pdf");
            when(file.getSize()).thenReturn(1024L);
            when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[1024]));

            FileReference fileRef = new FileReference(
                    "uploads/test-ref.pdf",
                    new FileMetadata("quotation.pdf", "application/pdf", 1024L, Instant.now(), ACTING_USER_ID)
            );
            when(fileStorageService.store(any(), any(FileMetadata.class))).thenReturn(fileRef);

            UUID attachmentId = UUID.randomUUID();
            when(attachmentRepository.save(any(Attachment.class))).thenAnswer(invocation -> {
                Attachment a = invocation.getArgument(0);
                a.setId(attachmentId);
                return a;
            });

            Attachment result = workOrderService.uploadEvidence(
                    WORK_ORDER_ID, PROPERTY_ID, file, "QUOTATION", ACTING_USER_ID);

            assertThat(result.getTicketId()).isEqualTo(TICKET_ID);
            assertThat(result.getOriginalFilename()).isEqualTo("quotation.pdf");
            assertThat(result.getStorageReference()).isEqualTo("uploads/test-ref.pdf");

            // Verify notification event is published
            ArgumentCaptor<WorkOrderAttachmentUploadedEvent> eventCaptor =
                    ArgumentCaptor.forClass(WorkOrderAttachmentUploadedEvent.class);
            verify(eventPublisher).publish(eventCaptor.capture());

            WorkOrderAttachmentUploadedEvent event = eventCaptor.getValue();
            assertThat(event.getWorkOrderId()).isEqualTo(WORK_ORDER_ID);
            assertThat(event.getTicketId()).isEqualTo(TICKET_ID);
            assertThat(event.getAttachmentId()).isEqualTo(attachmentId);
            assertThat(event.getAttachmentType()).isEqualTo("QUOTATION");
            assertThat(event.getPropertyId()).isEqualTo(PROPERTY_ID);
            assertThat(event.getActingUserId()).isEqualTo(ACTING_USER_ID);
        }

        @Test
        void uploadEvidence_withNonExistentWorkOrder_throwsException() throws IOException {
            when(workOrderRepository.findByIdAndPropertyId(WORK_ORDER_ID, PROPERTY_ID))
                    .thenReturn(Optional.empty());

            MultipartFile file = mock(MultipartFile.class);

            assertThatThrownBy(() -> workOrderService.uploadEvidence(
                    WORK_ORDER_ID, PROPERTY_ID, file, "QUOTATION", ACTING_USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("WorkOrder");

            verify(fileStorageService, never()).store(any(), any());
            verify(attachmentRepository, never()).save(any());
        }
    }

    // --- transition tests ---

    @Nested
    class TransitionTests {

        @Test
        void transition_toCreated_throwsException() {
            assertThatThrownBy(() -> workOrderService.transition(
                    WORK_ORDER_ID, PROPERTY_ID, WorkOrderStatus.CREATED, ACTING_USER_ID))
                    .isInstanceOf(InvalidTransitionException.class);
        }
    }

    // --- helper methods ---

    private Vendor createActiveVendor() {
        Vendor vendor = Vendor.builder()
                .name("Test Vendor")
                .contactEmail("vendor@example.com")
                .isActive(true)
                .build();
        vendor.setId(VENDOR_ID);
        vendor.setPropertyId(PROPERTY_ID);
        return vendor;
    }

    private Vendor createInactiveVendor() {
        Vendor vendor = Vendor.builder()
                .name("Inactive Vendor")
                .contactEmail("inactive@example.com")
                .isActive(false)
                .build();
        vendor.setId(VENDOR_ID);
        vendor.setPropertyId(PROPERTY_ID);
        return vendor;
    }

    private WorkOrder createWorkOrder(WorkOrderStatus status) {
        WorkOrder workOrder = WorkOrder.builder()
                .ticketId(TICKET_ID)
                .vendorId(VENDOR_ID)
                .status(status)
                .build();
        workOrder.setId(WORK_ORDER_ID);
        workOrder.setPropertyId(PROPERTY_ID);
        return workOrder;
    }
}
