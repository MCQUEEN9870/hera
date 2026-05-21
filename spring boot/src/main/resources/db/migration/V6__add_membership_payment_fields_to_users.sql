-- Add premium membership payment metadata onto users table.
-- NOTE: Safe to use as V6 only if the old V6 was never applied.

ALTER TABLE IF EXISTS users
    ADD COLUMN IF NOT EXISTS membership_plan VARCHAR(32);

ALTER TABLE IF EXISTS users
    ADD COLUMN IF NOT EXISTS membership_amount_paid_inr INTEGER;

ALTER TABLE IF EXISTS users
    ADD COLUMN IF NOT EXISTS membership_payment_id VARCHAR(64);

-- Optional: query helpers
CREATE INDEX IF NOT EXISTS ix_users_membership_payment_id
    ON users(membership_payment_id);
