-- Drop legacy archive tables (we now keep everything in deletion_audit)
-- Safe to run even if tables are already absent.

DROP TABLE IF EXISTS deleted_vehicle_registrations CASCADE;
DROP TABLE IF EXISTS deleted_user_accounts CASCADE;
