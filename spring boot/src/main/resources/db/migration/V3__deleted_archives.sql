-- Stores deletion audit + snapshots so admin can investigate fake accounts
-- and manually clean up any orphaned storage objects.

CREATE TABLE IF NOT EXISTS deleted_user_accounts (
    id BIGSERIAL PRIMARY KEY,
    original_user_id BIGINT,
    contact_number TEXT,
    email TEXT,
    full_name TEXT,
    membership TEXT,
    profile_photo_url TEXT,
    join_date TIMESTAMPTZ,
    verified BOOLEAN,
    rating INTEGER,
    review_text TEXT,
    deleted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_by_contact TEXT,
    deletion_reason TEXT,
    deletion_user_agent TEXT,
    deletion_ip TEXT,
    user_snapshot JSONB,
    vehicles_snapshot JSONB
);

CREATE INDEX IF NOT EXISTS idx_deleted_user_accounts_contact ON deleted_user_accounts(contact_number);
CREATE INDEX IF NOT EXISTS idx_deleted_user_accounts_deleted_at ON deleted_user_accounts(deleted_at);

CREATE TABLE IF NOT EXISTS deleted_vehicle_registrations (
    id BIGSERIAL PRIMARY KEY,
    deleted_user_account_id BIGINT REFERENCES deleted_user_accounts(id) ON DELETE SET NULL,
    original_registration_id BIGINT,
    original_user_id BIGINT,
    contact_number TEXT,
    vehicle_plate_number TEXT,
    vehicle_type TEXT,
    state TEXT,
    city TEXT,
    pincode TEXT,
    registration_date DATE,
    membership TEXT,
    rc_url TEXT,
    dl_url TEXT,
    vehicle_image_urls JSONB,
    folder_paths JSONB,
    deleted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_by_contact TEXT,
    deletion_source TEXT,
    deletion_user_agent TEXT,
    deletion_ip TEXT,
    registration_snapshot JSONB
);

CREATE INDEX IF NOT EXISTS idx_deleted_vehicle_registrations_contact ON deleted_vehicle_registrations(contact_number);
CREATE INDEX IF NOT EXISTS idx_deleted_vehicle_registrations_deleted_at ON deleted_vehicle_registrations(deleted_at);
CREATE INDEX IF NOT EXISTS idx_deleted_vehicle_registrations_orig_reg ON deleted_vehicle_registrations(original_registration_id);
