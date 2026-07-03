-- ============================================================
-- V1: Core schema for StrataResolve platform
-- Creates all entity tables, indexes, constraints, and
-- the reference_number_sequence table.
-- ============================================================

-- ============================================================
-- TABLES
-- ============================================================

-- Property
CREATE TABLE property (
    id              UUID            PRIMARY KEY,
    name            VARCHAR(255)    NOT NULL,
    code            VARCHAR(50)     NOT NULL UNIQUE,
    address         VARCHAR(500)    NOT NULL,
    timezone        VARCHAR(100)    NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Block
CREATE TABLE block (
    id              UUID            PRIMARY KEY,
    property_id     UUID            NOT NULL REFERENCES property(id),
    name            VARCHAR(255)    NOT NULL,
    label           VARCHAR(255),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_block_name_per_property UNIQUE (property_id, name)
);

-- Unit
CREATE TABLE unit (
    id                  UUID            PRIMARY KEY,
    block_id            UUID            NOT NULL REFERENCES block(id),
    property_id         UUID            NOT NULL REFERENCES property(id),
    unit_number         VARCHAR(50)     NOT NULL,
    floor               INTEGER,
    type                VARCHAR(20)     NOT NULL,
    occupancy_status    VARCHAR(20)     NOT NULL DEFAULT 'VACANT',
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_unit_number_per_block UNIQUE (block_id, unit_number)
);

-- Users (table named 'users' to avoid reserved keyword conflict)
CREATE TABLE users (
    id              UUID            PRIMARY KEY,
    email           VARCHAR(255)    NOT NULL UNIQUE,
    password_hash   VARCHAR(255)    NOT NULL,
    first_name      VARCHAR(100)    NOT NULL,
    last_name       VARCHAR(100)    NOT NULL,
    phone           VARCHAR(50),
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Membership
CREATE TABLE membership (
    id              UUID            PRIMARY KEY,
    user_id         UUID            NOT NULL REFERENCES users(id),
    property_id     UUID            NOT NULL REFERENCES property(id),
    unit_id         UUID            REFERENCES unit(id),
    role            VARCHAR(30)     NOT NULL,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    effective_from  DATE            NOT NULL,
    effective_to    DATE,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Ticket
CREATE TABLE ticket (
    id                      UUID            PRIMARY KEY,
    property_id             UUID            NOT NULL REFERENCES property(id),
    submitted_by            UUID            NOT NULL REFERENCES users(id),
    unit_id                 UUID            NOT NULL REFERENCES unit(id),
    reference_number        VARCHAR(20)     NOT NULL UNIQUE,
    title                   VARCHAR(500)    NOT NULL,
    description             TEXT            NOT NULL,
    category                VARCHAR(30)     NOT NULL,
    priority                VARCHAR(20)     NOT NULL,
    status                  VARCHAR(30)     NOT NULL DEFAULT 'SUBMITTED',
    location                VARCHAR(500),
    acknowledgement_due_at  TIMESTAMP,
    resolution_due_at       TIMESTAMP,
    acknowledged_at         TIMESTAMP,
    resolved_at             TIMESTAMP,
    sla_status              VARCHAR(30)     NOT NULL DEFAULT 'ON_TRACK',
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Status History
CREATE TABLE status_history (
    id              UUID            PRIMARY KEY,
    ticket_id       UUID            NOT NULL REFERENCES ticket(id),
    previous_status VARCHAR(30)     NOT NULL,
    new_status      VARCHAR(30)     NOT NULL,
    changed_by      UUID            NOT NULL REFERENCES users(id),
    reason          VARCHAR(1000),
    changed_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Comment
CREATE TABLE comment (
    id              UUID            PRIMARY KEY,
    ticket_id       UUID            NOT NULL REFERENCES ticket(id),
    author_id       UUID            NOT NULL REFERENCES users(id),
    content         TEXT            NOT NULL,
    visibility      VARCHAR(20)     NOT NULL DEFAULT 'PUBLIC',
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Attachment
CREATE TABLE attachment (
    id                  UUID            PRIMARY KEY,
    ticket_id           UUID            NOT NULL REFERENCES ticket(id),
    uploaded_by         UUID            NOT NULL REFERENCES users(id),
    original_filename   VARCHAR(500)    NOT NULL,
    content_type        VARCHAR(100)    NOT NULL,
    file_size           BIGINT          NOT NULL,
    storage_reference   VARCHAR(500)    NOT NULL,
    uploaded_at         TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Assignment
CREATE TABLE assignment (
    id              UUID            PRIMARY KEY,
    ticket_id       UUID            NOT NULL REFERENCES ticket(id),
    assigned_to     UUID            NOT NULL REFERENCES users(id),
    type            VARCHAR(20)     NOT NULL,
    assigned_at     TIMESTAMP       NOT NULL DEFAULT NOW(),
    accepted_at     TIMESTAMP
);

-- Vendor
CREATE TABLE vendor (
    id              UUID            PRIMARY KEY,
    property_id     UUID            NOT NULL REFERENCES property(id),
    name            VARCHAR(255)    NOT NULL,
    contact_email   VARCHAR(255),
    contact_phone   VARCHAR(50),
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Work Order
CREATE TABLE work_order (
    id              UUID            PRIMARY KEY,
    ticket_id       UUID            NOT NULL REFERENCES ticket(id),
    vendor_id       UUID            NOT NULL REFERENCES vendor(id),
    property_id     UUID            NOT NULL REFERENCES property(id),
    status          VARCHAR(20)     NOT NULL DEFAULT 'CREATED',
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMP
);

-- SLA Policy
CREATE TABLE sla_policy (
    id                      UUID            PRIMARY KEY,
    property_id             UUID            NOT NULL REFERENCES property(id),
    category                VARCHAR(30),
    priority                VARCHAR(20),
    acknowledgement_hours   INTEGER         NOT NULL,
    resolution_hours        INTEGER         NOT NULL,
    is_default              BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Audit Event
CREATE TABLE audit_event (
    id                  UUID            PRIMARY KEY,
    property_id         UUID            NOT NULL REFERENCES property(id),
    event_type          VARCHAR(100)    NOT NULL,
    acting_user_id      UUID            NOT NULL REFERENCES users(id),
    target_entity_type  VARCHAR(100)    NOT NULL,
    target_entity_id    UUID            NOT NULL,
    previous_value      TEXT,
    new_value           TEXT,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Notification
CREATE TABLE notification (
    id                  UUID            PRIMARY KEY,
    property_id         UUID            NOT NULL REFERENCES property(id),
    recipient_user_id   UUID            NOT NULL REFERENCES users(id),
    ticket_id           UUID            REFERENCES ticket(id),
    event_type          VARCHAR(100)    NOT NULL,
    subject             VARCHAR(500)    NOT NULL,
    body                TEXT            NOT NULL,
    delivery_status     VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    attempt_count       INTEGER         NOT NULL DEFAULT 0,
    next_attempt_at     TIMESTAMP       NOT NULL DEFAULT NOW(),
    sent_at             TIMESTAMP,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Reference Number Sequence
CREATE TABLE reference_number_sequence (
    year            INTEGER         PRIMARY KEY,
    last_number     INTEGER         NOT NULL DEFAULT 0
);

-- ============================================================
-- INDEXES
-- ============================================================

-- Block indexes
CREATE INDEX idx_block_property_id ON block(property_id);

-- Unit indexes
CREATE INDEX idx_unit_block_id ON unit(block_id);
CREATE INDEX idx_unit_property_id ON unit(property_id);

-- Membership indexes
CREATE INDEX idx_membership_user_id ON membership(user_id);
CREATE INDEX idx_membership_property_id ON membership(property_id);
CREATE INDEX idx_membership_unit_id ON membership(unit_id);
CREATE INDEX idx_membership_active ON membership(property_id, user_id, is_active);

-- Ticket indexes
CREATE INDEX idx_ticket_property_id ON ticket(property_id);
CREATE INDEX idx_ticket_submitted_by ON ticket(submitted_by);
CREATE INDEX idx_ticket_unit_id ON ticket(unit_id);
CREATE INDEX idx_ticket_status ON ticket(status);
CREATE INDEX idx_ticket_category ON ticket(category);
CREATE INDEX idx_ticket_priority ON ticket(priority);
CREATE INDEX idx_ticket_reference_number ON ticket(reference_number);
CREATE INDEX idx_ticket_property_status ON ticket(property_id, status);
CREATE INDEX idx_ticket_sla_status ON ticket(sla_status);
CREATE INDEX idx_ticket_created_at ON ticket(created_at);

-- Status History indexes
CREATE INDEX idx_status_history_ticket_id ON status_history(ticket_id);
CREATE INDEX idx_status_history_changed_by ON status_history(changed_by);

-- Comment indexes
CREATE INDEX idx_comment_ticket_id ON comment(ticket_id);
CREATE INDEX idx_comment_author_id ON comment(author_id);

-- Attachment indexes
CREATE INDEX idx_attachment_ticket_id ON attachment(ticket_id);
CREATE INDEX idx_attachment_uploaded_by ON attachment(uploaded_by);

-- Assignment indexes
CREATE INDEX idx_assignment_ticket_id ON assignment(ticket_id);
CREATE INDEX idx_assignment_assigned_to ON assignment(assigned_to);

-- Vendor indexes
CREATE INDEX idx_vendor_property_id ON vendor(property_id);

-- Work Order indexes
CREATE INDEX idx_work_order_ticket_id ON work_order(ticket_id);
CREATE INDEX idx_work_order_vendor_id ON work_order(vendor_id);
CREATE INDEX idx_work_order_property_id ON work_order(property_id);
CREATE INDEX idx_work_order_status ON work_order(status);

-- SLA Policy indexes
CREATE INDEX idx_sla_policy_property_id ON sla_policy(property_id);
CREATE INDEX idx_sla_policy_lookup ON sla_policy(property_id, category, priority);

-- Audit Event indexes
CREATE INDEX idx_audit_event_property_id ON audit_event(property_id);
CREATE INDEX idx_audit_event_acting_user_id ON audit_event(acting_user_id);
CREATE INDEX idx_audit_event_target ON audit_event(target_entity_type, target_entity_id);
CREATE INDEX idx_audit_event_event_type ON audit_event(event_type);
CREATE INDEX idx_audit_event_created_at ON audit_event(created_at);

-- Notification indexes
CREATE INDEX idx_notification_property_id ON notification(property_id);
CREATE INDEX idx_notification_recipient_user_id ON notification(recipient_user_id);
CREATE INDEX idx_notification_ticket_id ON notification(ticket_id);
CREATE INDEX idx_notification_delivery_status ON notification(delivery_status);
CREATE INDEX idx_notification_next_attempt ON notification(delivery_status, next_attempt_at);

-- Users indexes
CREATE INDEX idx_users_email ON users(email);

-- ============================================================
-- SEED DATA
-- ============================================================

-- Initialize reference number sequence for the current year
INSERT INTO reference_number_sequence (year, last_number)
VALUES (EXTRACT(YEAR FROM NOW())::INTEGER, 0);
