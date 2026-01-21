-- Stores snapshots of users/vehicles at deletion time so admins can investigate abuse
-- and manually reconcile any orphaned storage objects.

CREATE TABLE IF NOT EXISTS deletion_audit (
    id BIGSERIAL PRIMARY KEY,
    event_type TEXT NOT NULL, -- e.g. 'ACCOUNT_DELETE', 'VEHICLE_DELETE'
    deleted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    actor_contact TEXT,          -- who performed the deletion (JWT subject)
    actor_ip TEXT,
    actor_user_agent TEXT,

    subject_contact TEXT,        -- the user being deleted (or owner of vehicle)
    subject_user_id BIGINT,
    registration_id BIGINT,

    payload JSONB NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_deletion_audit_deleted_at ON deletion_audit (deleted_at DESC);
CREATE INDEX IF NOT EXISTS idx_deletion_audit_event_type ON deletion_audit (event_type);
CREATE INDEX IF NOT EXISTS idx_deletion_audit_subject_contact ON deletion_audit (subject_contact);
CREATE INDEX IF NOT EXISTS idx_deletion_audit_subject_user_id ON deletion_audit (subject_user_id);
CREATE INDEX IF NOT EXISTS idx_deletion_audit_registration_id ON deletion_audit (registration_id);
