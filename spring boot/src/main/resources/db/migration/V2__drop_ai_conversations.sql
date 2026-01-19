-- Removes the AI assistant conversation table(s) if present.
-- Safe for repeated runs.

DROP TABLE IF EXISTS ai_conversations CASCADE;
-- In case a typo table was created at some point
DROP TABLE IF EXISTS ai_coversations CASCADE;
