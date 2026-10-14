CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS tickets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_type_id UUID NOT NULL,
    show_time_id UUID NOT NULL,
    order_id UUID NOT NULL,
    seat_id UUID,
    qr_payload TEXT UNIQUE,
    seat_number VARCHAR(20),
    status VARCHAR(30) NOT NULL DEFAULT 'SOLD' CHECK (status IN ('SOLD', 'USED', 'CANCELLED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS check_ins (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL UNIQUE REFERENCES tickets(id) ON DELETE CASCADE,
    staff_id UUID NOT NULL,
    method VARCHAR(30) NOT NULL DEFAULT 'QR_SCAN' CHECK (method IN ('QR_SCAN', 'MANUAL')),
    checked_in_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for tickets
CREATE INDEX IF NOT EXISTS idx_tickets_order_id ON tickets(order_id);
CREATE INDEX IF NOT EXISTS idx_tickets_show_time_id ON tickets(show_time_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_tickets_qr_payload ON tickets(qr_payload);

-- Indexes for check_ins
CREATE UNIQUE INDEX IF NOT EXISTS uq_check_ins_ticket_id ON check_ins(ticket_id);
CREATE INDEX IF NOT EXISTS idx_check_ins_staff_id ON check_ins(staff_id);
CREATE INDEX IF NOT EXISTS idx_check_ins_checked_in_at ON check_ins(checked_in_at);
