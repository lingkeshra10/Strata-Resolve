-- ============================================================
-- V3: Duplicate detection support
-- Adds duplicate detection flag to ticket table and a
-- linking table for manually linked duplicate tickets.
-- ============================================================

-- Add duplicate flag to ticket table
ALTER TABLE ticket
    ADD COLUMN duplicate_flag BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN linked_to_ticket_id UUID REFERENCES ticket(id);

-- Create ticket_duplicate_link table for many-to-many linking
CREATE TABLE ticket_duplicate_link (
    id              UUID            PRIMARY KEY,
    primary_ticket_id UUID          NOT NULL REFERENCES ticket(id),
    duplicate_ticket_id UUID        NOT NULL REFERENCES ticket(id),
    linked_by       UUID            NOT NULL REFERENCES users(id),
    linked_at       TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_duplicate_link UNIQUE (primary_ticket_id, duplicate_ticket_id),
    CONSTRAINT chk_not_self_link CHECK (primary_ticket_id != duplicate_ticket_id)
);

-- Indexes for duplicate detection queries
CREATE INDEX idx_ticket_duplicate_flag ON ticket(property_id, duplicate_flag) WHERE duplicate_flag = TRUE;
CREATE INDEX idx_ticket_property_category_location ON ticket(property_id, category, location, created_at);
CREATE INDEX idx_ticket_duplicate_link_primary ON ticket_duplicate_link(primary_ticket_id);
CREATE INDEX idx_ticket_duplicate_link_duplicate ON ticket_duplicate_link(duplicate_ticket_id);
