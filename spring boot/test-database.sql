-- Test script to check if user_sessions table exists and functions work
-- Run this in your Supabase SQL editor

-- Check if table exists
SELECT table_name 
FROM information_schema.tables 
WHERE table_schema = 'public' 
AND table_name = 'user_sessions';

-- Check if functions exist
SELECT routine_name 
FROM information_schema.routines 
WHERE routine_schema = 'public' 
AND routine_name IN ('get_live_user_count', 'update_session_heartbeat', 'cleanup_expired_sessions', 'remove_session');

-- Test the functions
SELECT get_live_user_count('posts') as current_count;

-- Test session creation
SELECT update_session_heartbeat('test_session_123', 'posts');

-- Check if session was created
SELECT * FROM user_sessions WHERE session_id = 'test_session_123';

-- Test count again
SELECT get_live_user_count('posts') as count_after_test;

-- Clean up test session
SELECT remove_session('test_session_123');

-- Final count
SELECT get_live_user_count('posts') as final_count;
