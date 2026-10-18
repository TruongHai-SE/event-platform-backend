CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Table: payments
CREATE TABLE IF NOT EXISTS payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL,
    order_code VARCHAR(50),
    method VARCHAR(30) NOT NULL CHECK (method IN ('VNPAY', 'MOMO', 'SEPAY')),
    transaction_id VARCHAR(100),
    amount NUMERIC(15,2) NOT NULL CHECK (amount >= 0),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'CANCELLED', 'REFUNDED')),
    paid_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Table: refund_logs
CREATE TABLE IF NOT EXISTS refund_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL,
    payment_id UUID NOT NULL REFERENCES payments(id) ON DELETE RESTRICT,
    amount NUMERIC(15,2) NOT NULL CHECK (amount >= 0),
    reason TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'PROCESSING' CHECK (status IN ('PROCESSING', 'SUCCESS', 'FAILED', 'MANUAL_BANK')),
    gateway_refund_id VARCHAR(100),
    idempotency_key VARCHAR(100),
    refunded_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Partial unique indexes & search indexes for payments
CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_method_transaction_id
    ON payments (method, transaction_id)
    WHERE transaction_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_one_settled_payment_per_order
    ON payments (order_id)
    WHERE status IN ('SUCCESS', 'REFUNDED');

CREATE INDEX IF NOT EXISTS idx_payments_order_id ON payments (order_id);
CREATE INDEX IF NOT EXISTS idx_payments_order_code ON payments (order_code);

-- Partial unique indexes & search indexes for refund_logs
CREATE UNIQUE INDEX IF NOT EXISTS uq_refund_logs_idempotency_key
    ON refund_logs (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_refund_logs_method_gateway_refund_id
    ON refund_logs (gateway_refund_id)
    WHERE gateway_refund_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_refund_logs_order_id ON refund_logs (order_id);
CREATE INDEX IF NOT EXISTS idx_refund_logs_payment_id ON refund_logs (payment_id);
