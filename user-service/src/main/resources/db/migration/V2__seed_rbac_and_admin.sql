-- 1. Nạp danh mục 4 Roles chuẩn theo yêu cầu hệ thống Event & Festival Platform
INSERT INTO roles (name, description) VALUES
('ROLE_ADMIN', 'Quản trị viên toàn quyền hệ thống'),
('ROLE_ORGANIZER', 'Ban tổ chức sự kiện và lễ hội'),
('ROLE_ATTENDEE', 'Người mua vé và tham dự sự kiện'),
('ROLE_STAFF', 'Nhân viên hỗ trợ và soát vé tại sự kiện')
ON CONFLICT (name) DO NOTHING;

-- 2. Nạp danh mục Permissions thực tế
INSERT INTO permissions (name, description) VALUES
('event:create', 'Quyền tạo mới sự kiện'),
('event:update', 'Quyền chỉnh sửa thông tin sự kiện'),
('event:delete', 'Quyền xóa hoặc hủy sự kiện'),
('event:view', 'Quyền xem danh sách và chi tiết sự kiện'),
('ticket:manage', 'Quyền quản lý loại vé và số lượng mở bán'),
('ticket:checkin', 'Quyền quét mã QR check-in vé'),
('user:manage', 'Quyền quản lý danh sách người dùng'),
('role:manage', 'Quyền cấu hình vai trò và phân quyền')
ON CONFLICT (name) DO NOTHING;

-- 3. Gán Permissions cho Roles
-- ROLE_ADMIN: Toàn quyền hệ thống
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.name = 'ROLE_ADMIN'
ON CONFLICT DO NOTHING;

-- ROLE_ORGANIZER: Quản lý sự kiện và vé
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ROLE_ORGANIZER' AND p.name IN ('event:create', 'event:update', 'event:view', 'ticket:manage')
ON CONFLICT DO NOTHING;

-- ROLE_ATTENDEE: Xem sự kiện và mua vé
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ROLE_ATTENDEE' AND p.name IN ('event:view')
ON CONFLICT DO NOTHING;

-- ROLE_STAFF: Xem sự kiện và soát vé tại cửa
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ROLE_STAFF' AND p.name IN ('event:view', 'ticket:checkin')
ON CONFLICT DO NOTHING;

