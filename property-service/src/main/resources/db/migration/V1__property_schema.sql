-- ============================================================
-- StrataResolve - Property Service
-- Initial Database Schema
--
-- Tables:
--   1. property
--   2. block
--   3. unit
--
-- Database: PostgreSQL
-- ============================================================

-- ============================================================
-- 1. PROPERTY
-- ============================================================

CREATE TABLE property (
    id              UUID            PRIMARY KEY,
    name            VARCHAR(255)    NOT NULL,
    code            VARCHAR(50)     NOT NULL,
    address         VARCHAR(500)    NOT NULL,
    timezone        VARCHAR(100)    NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_property_code UNIQUE (code),
    CONSTRAINT chk_property_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

-- ============================================================
-- 2. BLOCK
-- ============================================================

CREATE TABLE block (
    id              UUID            PRIMARY KEY,
    property_id     UUID            NOT NULL,
    name            VARCHAR(255)    NOT NULL,
    label           VARCHAR(255),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_block_property FOREIGN KEY (property_id) REFERENCES property(id) ON DELETE RESTRICT,
    CONSTRAINT uq_block_name_per_property UNIQUE (property_id, name),
    CONSTRAINT uq_block_id_property UNIQUE (id, property_id)
);

-- ============================================================
-- 3. UNIT
-- ============================================================

CREATE TABLE unit (
    id                  UUID            PRIMARY KEY,
    block_id            UUID            NOT NULL,
    property_id         UUID            NOT NULL,
    unit_number         VARCHAR(50)     NOT NULL,
    floor               INTEGER,
    type                VARCHAR(20)     NOT NULL,
    occupancy_status    VARCHAR(20)     NOT NULL DEFAULT 'VACANT',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_unit_property FOREIGN KEY (property_id) REFERENCES property(id) ON DELETE RESTRICT,
    CONSTRAINT fk_unit_block_property FOREIGN KEY (block_id, property_id) REFERENCES block(id, property_id) ON DELETE RESTRICT,
    CONSTRAINT uq_unit_number_per_block UNIQUE (block_id, unit_number),
    CONSTRAINT chk_unit_type
        CHECK (type IN (
            'RESIDENTIAL',
            'COMMERCIAL',
            'PARKING'
        )),
    CONSTRAINT chk_unit_occupancy_status
        CHECK (occupancy_status IN (
            'OCCUPIED',
            'VACANT'
        ))
);


-- ============================================================
-- 4. INDEXES
-- ============================================================

-- Supports:
--
-- UnitRepository.findByPropertyId(UUID propertyId)
--
CREATE INDEX idx_unit_property_id ON unit(property_id);