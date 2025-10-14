-- Create posts table (single image per post)
create extension if not exists pgcrypto;

create table if not exists posts (
  id uuid primary key default gen_random_uuid(),
  user_id bigint not null,

  content text not null check (char_length(content) between 50 and 500),
  background_css text,
  font_size smallint not null default 18 check (font_size between 16 and 48),
  is_light_text boolean not null default true,

  category text not null check (category in (
    'Vehicle Requirement','Transport Offer','Service Promotion','General Query','Local Updates'
  )),

  image_url text,

  status text not null default 'active' check (status in ('active','expired','removed')),
  created_at timestamptz not null default now(),
  expires_at timestamptz not null default (now() + interval '24 hours'),

  details jsonb default '{}'::jsonb
);

-- Optional FK if users table exists and types match
-- alter table posts add constraint fk_posts_user
--   foreign key (user_id) references users(id) on delete cascade;

create index if not exists idx_posts_expires_at on posts (expires_at);
create index if not exists idx_posts_category_created on posts (category, created_at desc);
create index if not exists idx_posts_status on posts (status);
create index if not exists idx_posts_created_at on posts (created_at desc);


