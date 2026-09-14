-- ============================================================
-- V1: Ticket Service Schema
-- ============================================================
-- Database ownership:
--
-- Ticket Service owns:
--   - ticket
--   - status_history
--   - comment
--   - attachment
--   - assignment
--   - ticket_duplicate_link
--   - reference_number_sequence
--
-- External identifiers such as:
--   - property_id
--   - submitted_by
--   - unit_id
--   - changed_by
--   - author_id
--   - uploaded_by
--   - assigned_to
--
-- are owned by other microservices and therefore DO NOT have
-- cross-service foreign-key constraints.
-- ============================================================


-- ============================================================
-- TICKET
-- ============================================================

CREATE TABLE ticket
(
    id                      UUID            NOT NULL,
    property_id             UUID            NOT NULL,
    submitted_by            UUID            NOT NULL,
    unit_id                 UUID            NOT NULL,
    reference_number        VARCHAR(20)     NOT NULL,
    title                   VARCHAR(500)    NOT NULL,
    description             TEXT            NOT NULL,
    category                VARCHAR(30)     NOT NULL,
    priority                VARCHAR(20)     NOT NULL,
    status                  VARCHAR(30)     NOT NULL DEFAULT 'SUBMITTED',
    location                VARCHAR(500),
    acknowledgement_due_at  TIMESTAMPTZ,
    resolution_due_at       TIMESTAMPTZ,
    acknowledged_at         TIMESTAMPTZ,
    resolved_at             TIMESTAMPTZ,
    sla_status              VARCHAR(30)     NOT NULL DEFAULT 'ON_TRACK',
    duplicate_flag          BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_ticket
        PRIMARY KEY (id),

    CONSTRAINT uq_ticket_reference_number
        UNIQUE (reference_number)
);


-- ============================================================
-- STATUS HISTORY
-- ============================================================

CREATE TABLE status_history
(
    id                  UUID            NOT NULL,
    ticket_id           UUID            NOT NULL,
    previous_status     VARCHAR(30)     NOT NULL,
    new_status          VARCHAR(30)     NOT NULL,
    changed_by          UUID            NOT NULL,
    reason              VARCHAR(1000),
    changed_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_status_history
        PRIMARY KEY (id),

    CONSTRAINT fk_status_history_ticket
        FOREIGN KEY (ticket_id)
        REFERENCES ticket(id)
        ON DELETE CASCADE
);


-- ============================================================
-- COMMENT
-- ============================================================

CREATE TABLE comment
(
    id                  UUID            NOT NULL,
    ticket_id           UUID            NOT NULL,
    author_id           UUID            NOT NULL,
    content             TEXT            NOT NULL,
    visibility          VARCHAR(20)     NOT NULL DEFAULT 'PUBLIC',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_comment
        PRIMARY KEY (id),

    CONSTRAINT fk_comment_ticket
        FOREIGN KEY (ticket_id)
        REFERENCES ticket(id)
        ON DELETE CASCADE
);


-- ============================================================
-- ATTACHMENT
-- ============================================================

CREATE TABLE attachment
(
    id                  UUID            NOT NULL,
    ticket_id           UUID            NOT NULL,
    uploaded_by         UUID            NOT NULL,
    original_filename   VARCHAR(500)    NOT NULL,
    content_type        VARCHAR(100)    NOT NULL,
    file_size           BIGINT          NOT NULL,
    storage_reference   VARCHAR(500)    NOT NULL,
    uploaded_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_attachment
        PRIMARY KEY (id),

    CONSTRAINT fk_attachment_ticket
        FOREIGN KEY (ticket_id)
        REFERENCES ticket(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_attachment_file_size
        CHECK (file_size >= 0)
);


-- ============================================================
-- ASSIGNMENT
-- ============================================================

CREATE TABLE assignment
(
    id                  UUID            NOT NULL,
    ticket_id           UUID            NOT NULL,
    assigned_to         UUID            NOT NULL,
    type                VARCHAR(20)     NOT NULL,
    assigned_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    accepted_at         TIMESTAMPTZ,

    CONSTRAINT pk_assignment
        PRIMARY KEY (id),

    CONSTRAINT fk_assignment_ticket
        FOREIGN KEY (ticket_id)
        REFERENCES ticket(id)
        ON DELETE CASCADE
);


-- ============================================================
-- TICKET DUPLICATE LINK
-- ============================================================
-- Stores the relationship between a primary ticket and another
-- ticket identified as a possible duplicate.
--
-- Both tickets belong to Ticket Service, therefore database
-- foreign keys are appropriate here.
-- ============================================================

CREATE TABLE ticket_duplicate_link
(
    id                      UUID            NOT NULL,

    primary_ticket_id       UUID            NOT NULL,
    duplicate_ticket_id     UUID            NOT NULL,

    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_ticket_duplicate_link
        PRIMARY KEY (id),

    CONSTRAINT fk_ticket_duplicate_primary
        FOREIGN KEY (primary_ticket_id)
        REFERENCES ticket(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_ticket_duplicate_duplicate
        FOREIGN KEY (duplicate_ticket_id)
        REFERENCES ticket(id)
        ON DELETE CASCADE,

    CONSTRAINT uq_ticket_duplicate_link
        UNIQUE (
            primary_ticket_id,
            duplicate_ticket_id
        ),

    CONSTRAINT chk_ticket_not_duplicate_of_itself
        CHECK (
            primary_ticket_id <> duplicate_ticket_id
        )
);


-- ============================================================
-- REFERENCE NUMBER SEQUENCE
-- ============================================================
-- Used by ReferenceNumberGenerator.
--
-- Example:
--   2026 | 150
--
-- Together with pessimistic locking this allows atomic
-- generation of ticket reference numbers.
-- ============================================================

CREATE TABLE reference_number_sequence
(
    year            INTEGER     NOT NULL,
    last_number     INTEGER     NOT NULL DEFAULT 0,

    CONSTRAINT pk_reference_number_sequence
        PRIMARY KEY (year),

    CONSTRAINT chk_reference_number_non_negative
        CHECK (last_number >= 0)
);


-- ============================================================
-- TICKET INDEXES
-- ============================================================

-- Frequently used for property-scoped ticket queries.
CREATE INDEX idx_ticket_property_id ON ticket(property_id);


-- Supports:
-- findByPropertyIdAndStatus(...)
CREATE INDEX idx_ticket_property_status ON ticket(property_id, status);


-- Supports:
-- findBySubmittedBy(...)
CREATE INDEX idx_ticket_submitted_by ON ticket(submitted_by);

-- Supports:
-- rate limiting:
--
-- WHERE submitted_by = ?
--   AND created_at >= ?
CREATE INDEX idx_ticket_submitted_by_created_at ON ticket(submitted_by, created_at DESC);

-- Supports:
-- findByUnitId(...)
CREATE INDEX idx_ticket_unit_id ON ticket(unit_id);

-- Useful for operational status queries.
CREATE INDEX idx_ticket_status ON ticket(status);


-- Useful for category filtering/reporting.
CREATE INDEX idx_ticket_category
    ON ticket(category);


-- Useful for priority filtering/reporting.
CREATE INDEX idx_ticket_priority
    ON ticket(priority);


-- Useful for SLA state queries.
CREATE INDEX idx_ticket_sla_status
    ON ticket(sla_status);


-- Useful for chronological ticket listings.
CREATE INDEX idx_ticket_created_at
    ON ticket(created_at DESC);


-- Supports resident visibility queries such as:
--
-- WHERE property_id = ?
--   AND submitted_by = ?
CREATE INDEX idx_ticket_property_submitted_by ON ticket(property_id, submitted_by);


-- Supports resident visibility through linked units.
CREATE INDEX idx_ticket_property_unit_id ON ticket(property_id, unit_id);


-- ============================================================
-- DUPLICATE DETECTION INDEXES
-- ============================================================

-- Supports:
--
-- WHERE property_id = ?
--   AND category = ?
--   AND created_at >= ?
--   AND status NOT IN (...)
--
-- The query is used by duplicate detection.
CREATE INDEX idx_ticket_duplicate_candidate_lookup
    ON ticket(
        property_id,
        category,
        created_at DESC
    )
    WHERE status NOT IN (
        'CLOSED',
        'CANCELLED',
        'REJECTED'
    );


-- Supports:
--
-- findByPropertyIdAndDuplicateFlagTrue(...)
CREATE INDEX idx_ticket_property_duplicate_flag
    ON ticket(property_id)
    WHERE duplicate_flag = TRUE;


-- ============================================================
-- SLA MONITOR INDEXES
-- ============================================================
-- The SLA monitor looks for non-terminal tickets where either
-- acknowledgement or resolution deadlines have passed.
--
-- Partial indexes keep these indexes small and focused on
-- actionable tickets.
-- ============================================================

CREATE INDEX idx_ticket_acknowledgement_due
    ON ticket(acknowledgement_due_at)
    WHERE acknowledgement_due_at IS NOT NULL
      AND acknowledged_at IS NULL
      AND status NOT IN (
          'CLOSED',
          'CANCELLED',
          'REJECTED',
          'RESOLVED'
      );


CREATE INDEX idx_ticket_resolution_due
    ON ticket(resolution_due_at)
    WHERE resolution_due_at IS NOT NULL
      AND resolved_at IS NULL
      AND status NOT IN (
          'CLOSED',
          'CANCELLED',
          'REJECTED',
          'RESOLVED'
      );


-- ============================================================
-- STATUS HISTORY INDEXES
-- ============================================================

-- Supports:
--
-- findByTicketIdOrderByChangedAtAsc(...)
CREATE INDEX idx_status_history_ticket_changed_at
    ON status_history(
        ticket_id,
        changed_at ASC
    );


-- Useful for audit/history searches by acting user.
CREATE INDEX idx_status_history_changed_by
    ON status_history(changed_by);


-- ============================================================
-- COMMENT INDEXES
-- ============================================================

-- Supports:
--
-- findByTicketIdOrderByCreatedAtAsc(...)
CREATE INDEX idx_comment_ticket_created_at
    ON comment(
        ticket_id,
        created_at ASC
    );


-- Supports:
--
-- findByTicketIdAndVisibilityOrderByCreatedAtAsc(...)
CREATE INDEX idx_comment_ticket_visibility_created_at
    ON comment(
        ticket_id,
        visibility,
        created_at ASC
    );


-- Useful for locating comments created by a particular user.
CREATE INDEX idx_comment_author_id
    ON comment(author_id);


-- ============================================================
-- ATTACHMENT INDEXES
-- ============================================================

-- Supports:
--
-- findByTicketIdOrderByUploadedAtAsc(...)
CREATE INDEX idx_attachment_ticket_uploaded_at
    ON attachment(
        ticket_id,
        uploaded_at ASC
    );


CREATE INDEX idx_attachment_uploaded_by
    ON attachment(uploaded_by);


-- ============================================================
-- ASSIGNMENT INDEXES
-- ============================================================

-- Supports:
--
-- findByTicketId(...)
CREATE INDEX idx_assignment_ticket_id
    ON assignment(ticket_id);


-- Supports:
--
-- findByAssignedTo(...)
CREATE INDEX idx_assignment_assigned_to
    ON assignment(assigned_to);


-- Useful when retrieving a ticket's assignments chronologically.
CREATE INDEX idx_assignment_ticket_assigned_at
    ON assignment(
        ticket_id,
        assigned_at ASC
    );


-- ============================================================
-- TICKET DUPLICATE LINK INDEXES
-- ============================================================

-- Supports:
--
-- findByPrimaryTicketId(...)
--
-- The unique constraint also creates an index whose first
-- column is primary_ticket_id, so an additional
-- primary_ticket_id-only index is unnecessary.


-- Supports:
--
-- findByDuplicateTicketId(...)
CREATE INDEX idx_ticket_duplicate_link_duplicate
    ON ticket_duplicate_link(duplicate_ticket_id);


-- ============================================================
-- REFERENCE NUMBER SEED
-- ============================================================

INSERT INTO reference_number_sequence (
    year,
    last_number
)
VALUES (
    EXTRACT(YEAR FROM CURRENT_DATE)::INTEGER,
    0
)
ON CONFLICT (year) DO NOTHING;