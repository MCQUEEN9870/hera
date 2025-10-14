-- Add password_hash column if not exists
ALTER TABLE IF EXISTS users
ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);


