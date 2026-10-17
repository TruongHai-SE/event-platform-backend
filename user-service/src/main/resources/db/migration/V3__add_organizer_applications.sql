CREATE TABLE IF NOT EXISTS organizer_applications (
    id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id               UUID NOT NULL REFERENCES user_profiles(id) ON DELETE CASCADE,
    organization_name     VARCHAR(200) NOT NULL,
    organization_type     VARCHAR(30) NOT NULL DEFAULT 'INDIVIDUAL',
    representative_name   VARCHAR(150) NOT NULL,
    contact_email         VARCHAR(150) NOT NULL,
    contact_phone         VARCHAR(20) NOT NULL,
    address               VARCHAR(255),
    website_url           VARCHAR(500),
    bio                   TEXT,
    status                VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    reviewed_by           UUID,
    review_note           VARCHAR(1000),
    reviewed_at           TIMESTAMP WITH TIME ZONE,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_organizer_apps_user_id ON organizer_applications(user_id);
CREATE INDEX IF NOT EXISTS idx_organizer_apps_status ON organizer_applications(status);

-- Partial Unique Index: Chặn race condition 1 user nộp 2 đơn PENDING cùng lúc ở tầng Database
CREATE UNIQUE INDEX IF NOT EXISTS uq_organizer_apps_user_pending
    ON organizer_applications(user_id)
    WHERE status = 'PENDING';
