-- Default commercial configuration for a fresh deployment.  These inserts are
-- idempotent: deployments never overwrite prices configured by an Admin.
INSERT INTO ad_packages (id, name, description, price, duration_days, active)
VALUES
    ('10000000-0000-0000-0000-000000000001', 'Starter', 'Gói quảng cáo cơ bản', 100000, 7, TRUE),
    ('10000000-0000-0000-0000-000000000002', 'Standard', 'Gói quảng cáo tiêu chuẩn', 250000, 14, TRUE),
    ('10000000-0000-0000-0000-000000000003', 'Premium', 'Gói quảng cáo nổi bật', 500000, 30, TRUE)
ON CONFLICT (name) DO NOTHING;

INSERT INTO ad_position_prices (id, position, bonus_price, active)
VALUES
    ('20000000-0000-0000-0000-000000000001', 'HOME_TOP', 50000, TRUE),
    ('20000000-0000-0000-0000-000000000002', 'HOME_MIDDLE', 30000, TRUE),
    ('20000000-0000-0000-0000-000000000003', 'EVENT_DETAIL', 20000, TRUE)
ON CONFLICT (position) DO NOTHING;
