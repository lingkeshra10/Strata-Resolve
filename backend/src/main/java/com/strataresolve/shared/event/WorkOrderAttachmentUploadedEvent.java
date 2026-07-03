package com.strataresolve.shared.event;

import java.util.UUID;

/**
 * Published when a Vendor_Technician uploads a quotation or completion evidence to a Work_Order.
 * Triggers: notification to Property_Manager, audit.
 */
public class WorkOrderAttachmentUploadedEvent extends DomainEvent {

    private final UUID workOrderId;
    private final UUID ticketId;
    private final UUID attachmentId;
    private final String attachmentType;

    /**
     * @param actingUserId   the vendor technician who uploaded the file
     * @param propertyId     the property context
     * @param workOrderId    the work order the file was uploaded to
     * @param ticketId       the associated ticket
     * @param attachmentId   the stored attachment ID
     * @param attachmentType description of the upload type (e.g., "QUOTATION", "COMPLETION_EVIDENCE")
     */
    public WorkOrderAttachmentUploadedEvent(UUID actingUserId, UUID propertyId,
                                            UUID workOrderId, UUID ticketId,
                                            UUID attachmentId, String attachmentType) {
        super(actingUserId, propertyId);
        this.workOrderId = workOrderId;
        this.ticketId = ticketId;
        this.attachmentId = attachmentId;
        this.attachmentType = attachmentType;
    }

    public UUID getWorkOrderId() {
        return workOrderId;
    }

    public UUID getTicketId() {
        return ticketId;
    }

    public UUID getAttachmentId() {
        return attachmentId;
    }

    public String getAttachmentType() {
        return attachmentType;
    }
}
