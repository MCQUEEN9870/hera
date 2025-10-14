-- Create user_sessions table for tracking live users
create table if not exists user_sessions (
  id uuid primary key default gen_random_uuid(),
  session_id text not null unique,
  page text not null default 'posts',
  user_agent text,
  ip_address inet,
  last_heartbeat timestamptz not null default now(),
  created_at timestamptz not null default now(),
  expires_at timestamptz not null default (now() + interval '30 seconds')
);

-- Create indexes for better performance
create index if not exists idx_user_sessions_page on user_sessions (page);
create index if not exists idx_user_sessions_last_heartbeat on user_sessions (last_heartbeat);
create index if not exists idx_user_sessions_expires_at on user_sessions (expires_at);
create index if not exists idx_user_sessions_session_id on user_sessions (session_id);

-- Create a function to clean up expired sessions
create or replace function cleanup_expired_sessions()
returns void as $$
begin
  delete from user_sessions where expires_at < now();
end;
$$ language plpgsql;

-- Create a function to get live user count for a page
create or replace function get_live_user_count(page_name text default 'posts')
returns integer as $$
declare
  user_count integer;
begin
  -- Clean up expired sessions first
  perform cleanup_expired_sessions();
  
  -- Count active sessions for the page
  select count(*) into user_count
  from user_sessions
  where page = page_name
    and last_heartbeat > (now() - interval '15 seconds')
    and expires_at > now();
    
  return coalesce(user_count, 0);
end;
$$ language plpgsql;

-- Create a function to update session heartbeat
create or replace function update_session_heartbeat(
  session_id_param text,
  page_name text default 'posts'
)
returns void as $$
begin
  -- Update existing session or insert new one
  insert into user_sessions (session_id, page, last_heartbeat, expires_at)
  values (session_id_param, page_name, now(), now() + interval '30 seconds')
  on conflict (session_id) 
  do update set 
    last_heartbeat = now(),
    expires_at = now() + interval '30 seconds',
    page = page_name;
end;
$$ language plpgsql;

-- Create a function to remove session
create or replace function remove_session(session_id_param text)
returns void as $$
begin
  delete from user_sessions where session_id = session_id_param;
end;
$$ language plpgsql;
