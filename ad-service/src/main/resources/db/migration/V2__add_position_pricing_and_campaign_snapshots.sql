CREATE TABLE ad_position_prices (
    id UUID PRIMARY KEY,
    position VARCHAR(30) NOT NULL UNIQUE,
    bonus_price NUMERIC(15,2) NOT NULL CHECK (bonus_price >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE ad_campaigns
    ADD COLUMN package_price NUMERIC(15,2) NOT NULL DEFAULT 0,
    ADD COLUMN total_price NUMERIC(15,2) NOT NULL DEFAULT 0;

UPDATE ad_campaigns campaign
SET package_price = package.price,
    total_price = package.price
FROM ad_packages package
WHERE campaign.package_id = package.id;

CREATE TABLE campaign_positions (
    id UUID PRIMARY KEY,
    campaign_id UUID NOT NULL REFERENCES ad_campaigns(id) ON DELETE CASCADE,
    position VARCHAR(30) NOT NULL,
    bonus_price NUMERIC(15,2) NOT NULL CHECK (bonus_price >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_campaign_position UNIQUE (campaign_id, position)
);

CREATE INDEX idx_campaign_positions_campaign ON campaign_positions(campaign_id);
CREATE INDEX idx_ad_position_prices_active ON ad_position_prices(active, position);
