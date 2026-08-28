-- ============================================================
-- V1: Notification Service Schema
-- ============================================================
-- Owns notification delivery records for StrataResolve.
--
-- IMPORTANT:
-- property_id, recipient_user_id and ticket_id reference entities
-- owned by other microservices. Therefore NO cross-service
-- foreign-key constraints are created.
-- ============================================================


-- ============================================================
-- NOTIFICATION TABLE
-- ============================================================

CREATE TABLE notification
(
    id                  UUID            NOT NULL,
    property_id         UUID,
    recipient_user_id   UUID            NOT NULL,
    recipient_email     VARCHAR(255)    NOT NULL,
    ticket_id           UUID,
    event_type          VARCHAR(100)    NOT NULL,
    subject             VARCHAR(500)    NOT NULL,
    body                TEXT            NOT NULL,
    delivery_status     VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    attempt_count       INTEGER         NOT NULL DEFAULT 0,
    next_attempt_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    sent_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_notification
        PRIMARY KEY (id),

    CONSTRAINT chk_notification_attempt_count
        CHECK (attempt_count >= 0),

    CONSTRAINT chk_notification_delivery_status
        CHECK (
            delivery_status IN (
                'PENDING',
                'SENT',
                'FAILED'
            )
        )
);


-- ============================================================
-- INDEXES
-- ============================================================

-- Retrieve notifications belonging to a property.
CREATE INDEX idx_notification_property_id
    ON notification(property_id);

-- Retrieve notifications for a particular user.
CREATE INDEX idx_notification_recipient_user_id
    ON notification(recipient_user_id);

-- Useful for looking up notifications related to a ticket.
CREATE INDEX idx_notification_ticket_id
    ON notification(ticket_id);

-- Useful for filtering notification history by event type.
CREATE INDEX idx_notification_event_type
    ON notification(event_type);

-- Useful for querying notifications by delivery state.
CREATE INDEX idx_notification_delivery_status
    ON notification(delivery_status);

-- Most important index for your scheduled notification processor.
--
-- Supports:
--
-- WHERE delivery_status = 'PENDING'
--   AND next_attempt_at <= ?
--
CREATE INDEX idx_notification_pending_delivery
    ON notification(delivery_status, next_attempt_at);

-- Useful for user notification history:
--
-- WHERE recipient_user_id = ?
-- ORDER BY created_at DESC
--
CREATE INDEX idx_notification_recipient_created_at
    ON notification(recipient_user_id, created_at DESC);

-- Useful for property-level notification history.
CREATE INDEX idx_notification_property_created_at
    ON notification(property_id, created_at DESC);

-- Useful for operational/history queries.
CREATE INDEX idx_notification_created_at
    ON notification(created_at DESC);