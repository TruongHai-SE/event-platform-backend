-- 1. Bổ sung số điện thoại vào bảng accounts
ALTER TABLE accounts
    ADD COLUMN IF NOT EXISTS phone_number VARCHAR(20);

CREATE INDEX IF NOT EXISTS idx_accounts_phone_number ON accounts(phone_number);

-- 2. Mở rộng bảng verification_tokens để hỗ trợ OTP Quên mật khẩu & Mở khóa tài khoản chống brute-force
ALTER TABLE verification_tokens
    DROP CONSTRAINT IF EXISTS verification_tokens_token_value_key;

ALTER TABLE verification_tokens
    ADD COLUMN IF NOT EXISTS attempt_count INT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_verification_tokens_account_type
    ON verification_tokens(account_id, token_type);

-- 3. Bảng cấu hình chính sách bảo mật động (Admin Security Policy - Singleton id = 1)
CREATE TABLE IF NOT EXISTS security_policies (
    id                       SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    max_failed_attempts      INT NOT NULL DEFAULT 5 CHECK (max_failed_attempts BETWEEN 1 AND 20),
    lockout_duration_minutes INT NOT NULL DEFAULT 15 CHECK (lockout_duration_minutes BETWEEN 1 AND 10080),
    otp_expiration_minutes   INT NOT NULL DEFAULT 5 CHECK (otp_expiration_minutes BETWEEN 1 AND 60),
    max_otp_attempts         INT NOT NULL DEFAULT 5 CHECK (max_otp_attempts BETWEEN 1 AND 10),
    updated_by               UUID,
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO security_policies (id, max_failed_attempts, lockout_duration_minutes, otp_expiration_minutes, max_otp_attempts)
VALUES (1, 5, 15, 5, 5)
ON CONFLICT (id) DO NOTHING;
