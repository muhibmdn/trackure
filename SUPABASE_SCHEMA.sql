-- Flat-column schema used by CloudDtos.kt. Run in the project's SQL Editor.
-- This creates missing tables; it does not destroy/migrate legacy payload tables.
-- IDs for activities/logs are text because local seed and imported ICS IDs are not UUIDs.
begin;
do $$
begin
    if exists (select 1 from information_schema.columns where table_schema = 'public'
        and table_name in ('profiles', 'activities', 'activity_logs') and column_name = 'payload') then
        raise exception 'Legacy payload schema found. Migrate/archive those tables before applying the flat-column schema; no data has been changed.';
    end if;
end;
$$;
create table if not exists public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    name text not null,
    profile_url_or_email text not null default '',
    motto text not null default '',
    avatar_url text,
    updated_at timestamptz not null default now()
);
create table if not exists public.activities (
    id text not null,
    user_id uuid not null references auth.users(id) on delete cascade,
    title text not null,
    category text,
    scheduled_time text not null,
    full_screen_lockscreen_trigger boolean not null default false,
    loop_activity boolean not null default false,
    frequency text not null,
    repeat_days_of_week_csv text not null default '',
    specific_date_iso text,
    interval_count integer not null default 1 check (interval_count > 0),
    end_condition_type text not null,
    end_date text,
    end_occurrences integer,
    updated_at timestamptz not null default now(),
    primary key (id, user_id)
);
-- Deliberately no activity FK: history survives deletion and daily notes have no parent.
create table if not exists public.activity_logs (
    id text not null,
    user_id uuid not null references auth.users(id) on delete cascade,
    activity_id text not null,
    activity_title text not null,
    category text,
    scheduled_time text not null default '',
    date_iso text not null,
    is_completed boolean not null default false,
    completed_hour text,
    completed_minute text,
    note text,
    updated_at timestamptz not null default now(),
    primary key (id, user_id)
);
create index if not exists activities_user_idx on public.activities(user_id);
create index if not exists activity_logs_user_idx on public.activity_logs(user_id);

do $$
declare t text; owner_column text;
begin
    foreach t in array array['profiles', 'activities', 'activity_logs'] loop
        owner_column := case when t = 'profiles' then 'id' else 'user_id' end;
        execute format('alter table public.%I enable row level security', t);
        execute format('revoke all on public.%I from anon', t);
        execute format('grant select, insert, update, delete on public.%I to authenticated', t);
        execute format('drop policy if exists owner_select on public.%I', t);
        execute format('drop policy if exists owner_insert on public.%I', t);
        execute format('drop policy if exists owner_update on public.%I', t);
        execute format('drop policy if exists owner_delete on public.%I', t);
        execute format('create policy owner_select on public.%I for select to authenticated using ((select auth.uid()) = %I)', t, owner_column);
        execute format('create policy owner_insert on public.%I for insert to authenticated with check ((select auth.uid()) = %I)', t, owner_column);
        execute format('create policy owner_update on public.%I for update to authenticated using ((select auth.uid()) = %I) with check ((select auth.uid()) = %I)', t, owner_column, owner_column);
        execute format('create policy owner_delete on public.%I for delete to authenticated using ((select auth.uid()) = %I)', t, owner_column);
    end loop;
end;
$$;
commit;
