-- Run in the trackure project's Supabase SQL Editor before enabling cloud sync.
-- Payloads contain the serialized Room entity; owner_id is always the verified Auth UUID.
-- Composite keys also support existing local/ICS IDs without cross-account collisions.
begin;

create table if not exists public.profiles (
    id text not null,
    owner_id uuid not null references auth.users(id) on delete cascade,
    payload jsonb not null check (jsonb_typeof(payload) = 'object'),
    updated_at bigint not null,
    deleted boolean not null default false,
    primary key (id, owner_id),
    constraint profile_identity check (id = owner_id::text and payload->>'id' = id)
);

create table if not exists public.activities (
    id text not null,
    owner_id uuid not null references auth.users(id) on delete cascade,
    payload jsonb not null check (jsonb_typeof(payload) = 'object'),
    updated_at bigint not null,
    deleted boolean not null default false,
    primary key (id, owner_id)
);

-- No activity FK: history survives activity deletion and general notes have no parent.
create table if not exists public.activity_logs (
    id text not null,
    owner_id uuid not null references auth.users(id) on delete cascade,
    payload jsonb not null check (jsonb_typeof(payload) = 'object'),
    updated_at bigint not null,
    deleted boolean not null default false,
    primary key (id, owner_id)
);

create index if not exists activities_owner_idx on public.activities(owner_id);
create index if not exists activity_logs_owner_idx on public.activity_logs(owner_id);

-- Device timestamps provide last-write-wins ordering; keep device clocks accurate.
create or replace function public.trackure_keep_newest() returns trigger
language plpgsql set search_path = '' as $$
begin
    if new.updated_at < old.updated_at then return old; end if;
    return new;
end;
$$;

do $$
declare t text;
begin
    foreach t in array array['profiles', 'activities', 'activity_logs'] loop
        execute format('alter table public.%I enable row level security', t);
        execute format('revoke all on public.%I from anon', t);
        execute format('grant select, insert, update, delete on public.%I to authenticated', t);
        execute format('drop policy if exists owner_select on public.%I', t);
        execute format('drop policy if exists owner_insert on public.%I', t);
        execute format('drop policy if exists owner_update on public.%I', t);
        execute format('drop policy if exists owner_delete on public.%I', t);
        execute format('create policy owner_select on public.%I for select to authenticated using ((select auth.uid()) = owner_id)', t);
        execute format('create policy owner_insert on public.%I for insert to authenticated with check ((select auth.uid()) = owner_id)', t);
        execute format('create policy owner_update on public.%I for update to authenticated using ((select auth.uid()) = owner_id) with check ((select auth.uid()) = owner_id)', t);
        execute format('create policy owner_delete on public.%I for delete to authenticated using ((select auth.uid()) = owner_id)', t);
        execute format('drop trigger if exists trackure_keep_newest on public.%I', t);
        execute format('create trigger trackure_keep_newest before update on public.%I for each row execute function public.trackure_keep_newest()', t);
    end loop;
end;
$$;
commit;
