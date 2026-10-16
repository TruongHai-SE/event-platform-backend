CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Categories
CREATE TABLE IF NOT EXISTS categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(120) NOT NULL UNIQUE,
    description VARCHAR(500),
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    normalized_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_categories_normalized_name
    ON categories(normalized_name) WHERE status IN ('DRAFT', 'PUBLISHED');
CREATE INDEX IF NOT EXISTS idx_categories_status_sort
    ON categories(status, sort_order);

-- 2. Category Requests (Organizer proposes new category)
CREATE TABLE IF NOT EXISTS category_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_id UUID NOT NULL,
    proposed_name VARCHAR(100) NOT NULL,
    normalized_proposed_name VARCHAR(100) NOT NULL,
    description VARCHAR(1000),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    review_note VARCHAR(1000),
    reviewed_by UUID,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    category_id UUID REFERENCES categories(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_category_requests_requester ON category_requests(requester_id);
CREATE INDEX IF NOT EXISTS idx_category_requests_status ON category_requests(status, created_at);
CREATE UNIQUE INDEX IF NOT EXISTS uq_category_requests_pending_norm_name
    ON category_requests(normalized_proposed_name) WHERE status = 'PENDING';

-- 3. Events
CREATE TABLE IF NOT EXISTS events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organizer_id UUID NOT NULL,
    category_id UUID NOT NULL REFERENCES categories(id),
    title VARCHAR(500) NOT NULL,
    slug VARCHAR(550) NOT NULL UNIQUE,
    description TEXT,
    venue_name VARCHAR(255),
    venue_address VARCHAR(500),
    banner_url VARCHAR(500),
    banner_public_id VARCHAR(255),
    thumbnail_url VARCHAR(500),
    thumbnail_public_id VARCHAR(255),
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'PENDING', 'REJECTED', 'APPROVED', 'PUBLISHED', 'CANCELLED', 'ENDED')),
    seating_mode VARCHAR(30) NOT NULL DEFAULT 'GENERAL_ADMISSION'
        CHECK (seating_mode IN ('GENERAL_ADMISSION', 'ASSIGNED_SEAT')),
    refund_policy VARCHAR(30) NOT NULL DEFAULT 'NO_REFUND'
        CHECK (refund_policy IN ('NO_REFUND', 'ALLOW_REFUND')),
    refund_deadline_days INT CHECK (refund_deadline_days IS NULL OR refund_deadline_days >= 0),
    refund_fee_percentage NUMERIC(5,2) CHECK (refund_fee_percentage IS NULL OR (refund_fee_percentage >= 0 AND refund_fee_percentage <= 100)),
    rejection_reason TEXT,
    cancellation_reason TEXT,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    published_at TIMESTAMP WITH TIME ZONE,
    ended_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_events_organizer ON events(organizer_id);
CREATE INDEX IF NOT EXISTS idx_events_category ON events(category_id);
CREATE INDEX IF NOT EXISTS idx_events_status ON events(status);

-- 4. Show Times
CREATE TABLE IF NOT EXISTS show_times (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'SCHEDULED'
        CHECK (status IN ('SCHEDULED', 'CANCELLED', 'ENDED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_show_time_valid_range CHECK (end_time > start_time)
);
CREATE INDEX IF NOT EXISTS idx_show_times_event ON show_times(event_id);
CREATE INDEX IF NOT EXISTS idx_show_times_start ON show_times(start_time, end_time);

-- 5. Ticket Types
CREATE TABLE IF NOT EXISTS ticket_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    price NUMERIC(15,2) NOT NULL CHECK (price >= 0),
    max_per_order INT NOT NULL DEFAULT 4 CHECK (max_per_order >= 1),
    total_quantity INT NOT NULL CHECK (total_quantity >= 1),
    sale_start_time TIMESTAMP WITH TIME ZONE,
    sale_end_time TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ticket_types_event ON ticket_types(event_id);
CREATE INDEX IF NOT EXISTS idx_ticket_types_price ON ticket_types(price);

-- 6. Show Time Ticket Inventories
CREATE TABLE IF NOT EXISTS show_time_ticket_inventories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    show_time_id UUID NOT NULL REFERENCES show_times(id) ON DELETE CASCADE,
    ticket_type_id UUID NOT NULL REFERENCES ticket_types(id) ON DELETE CASCADE,
    total_quantity INT NOT NULL CHECK (total_quantity >= 0),
    reserved_quantity INT NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    sold_quantity INT NOT NULL DEFAULT 0 CHECK (sold_quantity >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_inventory_show_time_ticket_type UNIQUE (show_time_id, ticket_type_id),
    CONSTRAINT chk_inventory_not_oversold CHECK (reserved_quantity + sold_quantity <= total_quantity)
);
CREATE INDEX IF NOT EXISTS idx_inventory_show_time ON show_time_ticket_inventories(show_time_id);
CREATE INDEX IF NOT EXISTS idx_inventory_ticket_type ON show_time_ticket_inventories(ticket_type_id);

-- 7. Seat Sections (ASSIGNED_SEAT)
CREATE TABLE IF NOT EXISTS seat_sections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL,
    capacity INT NOT NULL DEFAULT 0 CHECK (capacity >= 0),
    sort_order INT NOT NULL DEFAULT 0,
    metadata TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_seat_sections_event_code UNIQUE (event_id, code)
);
CREATE INDEX IF NOT EXISTS idx_seat_sections_event ON seat_sections(event_id);

-- 8. Seats (ASSIGNED_SEAT - UNIQUE NULLS NOT DISTINCT for sectionless venues)
CREATE TABLE IF NOT EXISTS seats (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    section_id UUID REFERENCES seat_sections(id) ON DELETE CASCADE,
    ticket_type_id UUID REFERENCES ticket_types(id) ON DELETE SET NULL,
    row_label VARCHAR(20) NOT NULL,
    seat_number VARCHAR(20) NOT NULL,
    seat_label VARCHAR(50) NOT NULL,
    position_x NUMERIC(8,2),
    position_y NUMERIC(8,2),
    is_accessible BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_seats_event_section_row_number UNIQUE NULLS NOT DISTINCT (event_id, section_id, row_label, seat_number)
);
CREATE INDEX IF NOT EXISTS idx_seats_event ON seats(event_id);
CREATE INDEX IF NOT EXISTS idx_seats_section ON seats(section_id);
CREATE INDEX IF NOT EXISTS idx_seats_ticket_type ON seats(ticket_type_id);

-- 9. Seat Availabilities per ShowTime
CREATE TABLE IF NOT EXISTS seat_availabilities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    show_time_id UUID NOT NULL REFERENCES show_times(id) ON DELETE CASCADE,
    seat_id UUID NOT NULL REFERENCES seats(id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE'
        CHECK (status IN ('AVAILABLE', 'HELD', 'SOLD', 'BLOCKED')),
    held_until TIMESTAMP WITH TIME ZONE,
    order_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_seat_availabilities_show_time_seat UNIQUE (show_time_id, seat_id)
);
CREATE INDEX IF NOT EXISTS idx_seat_availabilities_show_time ON seat_availabilities(show_time_id);
CREATE INDEX IF NOT EXISTS idx_seat_availabilities_seat ON seat_availabilities(seat_id);

-- 10. Outbox Events for Event Broadcast (Task 34)
CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_outbox_events_status ON outbox_events(status, created_at);
