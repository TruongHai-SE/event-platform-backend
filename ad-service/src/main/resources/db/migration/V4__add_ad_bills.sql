CREATE TABLE ad_bills (
    id UUID PRIMARY KEY,
    bill_code VARCHAR(40) NOT NULL UNIQUE,
    organizer_id UUID NOT NULL,
    campaign_id UUID NOT NULL UNIQUE REFERENCES ad_campaigns(id),
    amount NUMERIC(15,2) NOT NULL CHECK (amount >= 0),
    status VARCHAR(30) NOT NULL CHECK (status IN ('PAID', 'PENDING_PAYMENT')),
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ad_bills_organizer_created_at ON ad_bills(organizer_id, created_at DESC);
