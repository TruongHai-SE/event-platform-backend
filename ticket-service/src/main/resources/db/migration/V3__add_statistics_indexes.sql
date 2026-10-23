CREATE INDEX IF NOT EXISTS idx_tickets_event_status ON tickets(event_id, status);
CREATE INDEX IF NOT EXISTS idx_tickets_showtime_status ON tickets(show_time_id, status);
CREATE INDEX IF NOT EXISTS idx_tickets_type_status ON tickets(ticket_type_id, status);
