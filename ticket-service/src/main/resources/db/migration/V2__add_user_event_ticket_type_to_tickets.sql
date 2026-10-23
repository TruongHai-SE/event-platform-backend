ALTER TABLE tickets ADD COLUMN IF NOT EXISTS user_id UUID;
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS event_id UUID;
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS ticket_type_name VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_tickets_user_id ON tickets(user_id);
CREATE INDEX IF NOT EXISTS idx_tickets_event_id ON tickets(event_id);
