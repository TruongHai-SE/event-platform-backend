CREATE TABLE promotions (
    id UUID PRIMARY KEY,
    event_id UUID,
    organizer_id UUID,
    code VARCHAR(50) NOT NULL UNIQUE,
    discount_type VARCHAR(20) NOT NULL,
    discount_value NUMERIC(15, 2) NOT NULL CHECK (discount_value > 0),
    min_order_amount NUMERIC(15, 2),
    max_discount_amount NUMERIC(15, 2),
    total_usage_limit INTEGER,
    used_count INTEGER NOT NULL DEFAULT 0 CHECK (used_count >= 0),
    max_usage_per_user INTEGER,
    is_compensation BOOLEAN NOT NULL DEFAULT FALSE,
    valid_from TIMESTAMP,
    valid_to TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_promotion_discount_type CHECK (discount_type IN ('PERCENTAGE', 'FIXED_AMOUNT')),
    CONSTRAINT chk_promotion_dates CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to > valid_from),
    CONSTRAINT chk_promotion_limits CHECK (total_usage_limit IS NULL OR total_usage_limit >= used_count)
);

CREATE INDEX idx_promotions_organizer_id ON promotions (organizer_id);
CREATE INDEX idx_promotions_event_id ON promotions (event_id);
CREATE INDEX idx_promotions_active ON promotions (is_active);

CREATE TABLE user_promotions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    promotion_id UUID NOT NULL REFERENCES promotions(id),
    source VARCHAR(30) NOT NULL,
    source_broadcast_id UUID,
    status VARCHAR(20) NOT NULL,
    issued_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_promotion UNIQUE (user_id, promotion_id)
);

CREATE TABLE promotion_usages (
    id UUID PRIMARY KEY,
    promotion_id UUID NOT NULL REFERENCES promotions(id),
    user_id UUID NOT NULL,
    order_id UUID NOT NULL,
    discount_amount NUMERIC(15, 2) NOT NULL CHECK (discount_amount >= 0),
    status VARCHAR(20) NOT NULL,
    used_at TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_promotion_usage_order UNIQUE (order_id)
);
