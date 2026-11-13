-- Global Nexus signaling metadata only. Screen, audio, and control data
-- travel peer-to-peer or through an opaque WebRTC TURN relay.

create table if not exists public.nexus_signaling_sessions (
    id uuid primary key,
    host_user_id uuid not null references auth.users(id) on delete cascade,
    creation_request_id uuid not null,
    guest_user_id uuid references auth.users(id) on delete cascade,
    pairing_secret_sha256 text not null
        check (pairing_secret_sha256 ~ '^[0-9a-f]{64}$'),
    created_at timestamptz not null default now(),
    join_expires_at timestamptz not null,
    expires_at timestamptz not null,
    closed_at timestamptz,
    constraint nexus_signaling_session_expiry_order
        check (expires_at > created_at and join_expires_at <= expires_at),
    constraint nexus_signaling_distinct_peers
        check (guest_user_id is null or guest_user_id <> host_user_id),
    constraint nexus_signaling_creation_idempotency
        unique (host_user_id, creation_request_id)
);

create index if not exists nexus_signaling_sessions_expiry_idx
    on public.nexus_signaling_sessions (expires_at);

create index if not exists nexus_signaling_sessions_host_recent_idx
    on public.nexus_signaling_sessions (host_user_id, created_at desc);

alter table public.nexus_signaling_sessions enable row level security;
revoke all on public.nexus_signaling_sessions from anon, authenticated;
grant all on public.nexus_signaling_sessions to service_role;

-- A SECURITY DEFINER predicate lets Realtime check membership without
-- granting clients direct reads of session rows or pairing-secret hashes.
create or replace function public.nexus_can_signal(channel_topic text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.nexus_signaling_sessions as session
        where ('nexus-session:' || session.id::text) = channel_topic
          and session.closed_at is null
          and session.expires_at > now()
          and (
              session.host_user_id = (select auth.uid())
              or session.guest_user_id = (select auth.uid())
          )
    );
$$;

revoke all on function public.nexus_can_signal(text) from public, anon;
grant execute on function public.nexus_can_signal(text) to authenticated;

drop policy if exists "Nexus paired peers receive private signaling"
    on realtime.messages;
create policy "Nexus paired peers receive private signaling"
    on realtime.messages
    for select
    to authenticated
    using (
        extension = 'broadcast'
        and public.nexus_can_signal(realtime.topic())
    );

drop policy if exists "Nexus paired peers send private signaling"
    on realtime.messages;
create policy "Nexus paired peers send private signaling"
    on realtime.messages
    for insert
    to authenticated
    with check (
        extension = 'broadcast'
        and public.nexus_can_signal(realtime.topic())
    );

create or replace function public.nexus_prune_expired_sessions()
returns bigint
language plpgsql
security definer
set search_path = ''
as $$
declare
    removed_count bigint;
begin
    delete from public.nexus_signaling_sessions
    where expires_at < now() - interval '15 minutes'
       or (closed_at is not null and closed_at < now() - interval '15 minutes');
    get diagnostics removed_count = row_count;
    return removed_count;
end;
$$;

revoke all on function public.nexus_prune_expired_sessions() from public, anon, authenticated;
grant execute on function public.nexus_prune_expired_sessions() to service_role;

-- The session table contains only short-lived peer IDs and pairing-secret
-- digests. Keep expiry cleanup independent of new-session traffic.
create extension if not exists pg_cron;
select cron.schedule(
    'nexus-signaling-session-retention',
    '*/10 * * * *',
    'select public.nexus_prune_expired_sessions()'
);

