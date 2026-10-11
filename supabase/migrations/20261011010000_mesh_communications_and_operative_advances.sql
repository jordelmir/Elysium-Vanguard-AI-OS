-- ══════════════════════════════════════════════════════════════════════════════
-- ELYSIUM BACKEND EXPANSION: MESH COMMUNICATIONS, LIVE VIDEO CALLS & OPERATIVE ADVANCES
-- Migration: 20261011010000_mesh_communications_and_operative_advances.sql
-- ══════════════════════════════════════════════════════════════════════════════
-- 1. Realtime Video & Audio Calls in Communication Sessions
-- 2. Extended Media Event Types (CALL, VIDEO_CALL, DTC_ALERT, FILE)
-- 3. Transitory Operative Advance Credit System (Tranches of ₡3,000 CRC when balance <= ₡400)
-- 4. Fleet Chat Cloud & Mesh Synchronization Table (fleet_chat_messages)
-- ══════════════════════════════════════════════════════════════════════════════

begin;

-- ── 1. EXPAND COMMUNICATION EVENT TYPES FOR CALLS & DTC ALERTS ──

-- Allow CALL, VIDEO_CALL, and DTC_ALERT in communication_events if constraint exists
do $$
begin
  alter table public.communication_events drop constraint if exists communication_events_event_type_check;
  alter table public.communication_events add constraint communication_events_event_type_check
    check (event_type in ('TEXT', 'IMAGE', 'AUDIO', 'FILE', 'REACTION', 'EDIT', 'REDACTION', 'MEMBERSHIP', 'CALL', 'VIDEO_CALL', 'DTC_ALERT'));
exception when undefined_table then
  null;
end $$;

-- Upgrade communication_publish_event_v2 to accept all supported content types
create or replace function public.communication_publish_event_v2(
    p_event_id uuid,
    p_conversation_id uuid,
    p_device_id uuid,
    p_envelope text,
    p_reply_to uuid,
    p_created_at timestamptz,
    p_event_type text
) returns setof public.communication_events
language plpgsql security definer set search_path=public,pg_temp as $$
declare v_existing public.communication_events; v_json jsonb;
begin
    if not public.communication_can_send(p_conversation_id, p_device_id) then
        raise exception 'COMMUNICATION_UNAVAILABLE' using errcode='42501';
    end if;
    if p_event_type not in ('TEXT', 'AUDIO', 'IMAGE', 'FILE', 'CALL', 'VIDEO_CALL', 'DTC_ALERT') then
        raise exception 'INVALID_EVENT_TYPE';
    end if;
    if p_event_id is null or p_created_at is null or length(p_envelope) not between 32 and 2097152 then
        raise exception 'INVALID_MESSAGE';
    end if;
    v_json := p_envelope::jsonb;
    if coalesce(v_json->>'version','') <> '1' or coalesce(jsonb_typeof(v_json->'recipients'),'') <> 'object'
       or v_json->>'ciphertext' is null or v_json->>'nonce' is null then
        raise exception 'INVALID_ENVELOPE';
    end if;
    if not (v_json->'recipients' ? p_device_id::text)
       or exists(select 1 from jsonb_object_keys(v_json->'recipients') k where not exists(
           select 1 from communication_devices d join communication_participants p on p.principal_id=d.principal_id
           where d.device_id::text=k and p.conversation_id=p_conversation_id and p.membership_state='ACTIVE'
               and d.revoked_at is null and d.verification_state<>'REVOKED' and d.transport_protocol='RSA_OAEP_AES_GCM_V1'))
       or exists(select 1 from communication_participants p where p.conversation_id=p_conversation_id and p.membership_state='ACTIVE'
           and not exists(select 1 from communication_devices d where d.principal_id=p.principal_id
               and d.revoked_at is null and d.verification_state<>'REVOKED' and d.transport_protocol='RSA_OAEP_AES_GCM_V1'
               and v_json->'recipients' ? d.device_id::text)) then
        raise exception 'RECIPIENT_KEYS_REQUIRED';
    end if;
    if p_reply_to is not null and not exists(select 1 from communication_events where event_id=p_reply_to and conversation_id=p_conversation_id) then
        raise exception 'INVALID_REPLY';
    end if;
    perform pg_advisory_xact_lock(hashtextextended('communication:'||p_conversation_id::text, 0));
    select * into v_existing from communication_events where event_id=p_event_id;
    if found then
        if v_existing.event_type<>p_event_type or v_existing.sender_id<>auth.uid() or v_existing.sender_device_id<>p_device_id
            or v_existing.conversation_id<>p_conversation_id or v_existing.encrypted_envelope<>p_envelope
            or v_existing.reply_to_event_id is distinct from p_reply_to or v_existing.client_created_at is distinct from p_created_at then
            raise exception 'IDEMPOTENCY_VIOLATION';
        end if;
        return next v_existing;
        return;
    end if;
    return query insert into communication_events(event_id, conversation_id, sender_id, sender_device_id, event_type,
        encrypted_envelope, reply_to_event_id, idempotency_key, client_created_at)
    values(p_event_id, p_conversation_id, auth.uid(), p_device_id, p_event_type, p_envelope, p_reply_to, p_event_id, p_created_at) returning *;
end; $$;

revoke all on function public.communication_publish_event_v2(uuid,uuid,uuid,text,uuid,timestamptz,text) from public, anon;
grant execute on function public.communication_publish_event_v2(uuid,uuid,uuid,text,uuid,timestamptz,text) to authenticated;

-- ── 2. REALTIME VIDEO & AUDIO CALLS IN COMMUNICATION CALL SESSIONS ──

-- Upgrade communication_start_call_v1 to support media_type (AUDIO or VIDEO)
create or replace function public.communication_start_call_v1(
    p_conversation_id uuid,
    p_call_id uuid,
    p_media_type text default 'AUDIO'
)
returns setof public.communication_call_sessions language plpgsql security definer set search_path=public,pg_temp as $$
declare
    v_peer uuid;
    v_permission text;
    v_existing public.communication_call_sessions;
    v_effective_media text;
begin
    if not communication_call_allowed_v1(p_conversation_id) then
        raise exception 'CALL_UNAVAILABLE' using errcode='42501';
    end if;

    v_effective_media := upper(trim(coalesce(p_media_type, 'AUDIO')));
    if v_effective_media not in ('AUDIO', 'VIDEO') then
        raise exception 'INVALID_MEDIA_TYPE';
    end if;

    select principal_id into v_peer from communication_participants
    where conversation_id=p_conversation_id and membership_state='ACTIVE' and principal_id<>auth.uid();

    select call_permission into v_permission from communication_privacy_settings where principal_id=v_peer;
    if coalesce(v_permission,'CONTACTS') not in ('EVERYONE','CONTACTS') or
       (coalesce(v_permission,'CONTACTS')='CONTACTS' and not communication_are_contacts(auth.uid(), v_peer)) then
        raise exception 'CALL_PRIVACY_REJECTED' using errcode='42501';
    end if;

    perform pg_advisory_xact_lock(hashtextextended('communication-call:'||p_conversation_id::text, 0));
    select * into v_existing from communication_call_sessions where id=p_call_id;
    if found then
        if v_existing.initiated_by<>auth.uid() or v_existing.conversation_id<>p_conversation_id then
            raise exception 'CALL_IDEMPOTENCY_VIOLATION';
        end if;
        return next v_existing;
        return;
    end if;

    update communication_call_sessions
    set state = case when state='RINGING' then 'MISSED' else 'ENDED' end,
        ended_at = now(),
        media_key = null
    where conversation_id=p_conversation_id and state in ('RINGING','ACTIVE') and last_activity_at < now() - interval '2 minutes';

    if exists(select 1 from communication_call_sessions where conversation_id=p_conversation_id and state in ('RINGING','ACTIVE')) then
        raise exception 'CALL_BUSY';
    end if;

    return query insert into communication_call_sessions(
        id, conversation_id, initiated_by, media_type, state, livekit_room_name, media_key
    ) values (
        p_call_id,
        p_conversation_id,
        auth.uid(),
        v_effective_media,
        'RINGING',
        'elysium_call_' || gen_random_uuid()::text,
        encode(extensions.gen_random_bytes(32), 'base64')
    ) returning *;
end $$;

-- Preserve signature compatibility with 2 arguments
create or replace function public.communication_start_call_v1(
    p_conversation_id uuid,
    p_call_id uuid
)
returns setof public.communication_call_sessions language plpgsql security definer set search_path=public,pg_temp as $$
begin
    return query select * from public.communication_start_call_v1(p_conversation_id, p_call_id, 'AUDIO');
end $$;

revoke all on function public.communication_start_call_v1(uuid,uuid,text) from public, anon;
grant execute on function public.communication_start_call_v1(uuid,uuid,text) to authenticated;
revoke all on function public.communication_start_call_v1(uuid,uuid) from public, anon;
grant execute on function public.communication_start_call_v1(uuid,uuid) to authenticated;

-- ── 3. OPERATIVE ADVANCES & TRANSITORY LOANS (3,000 CRC TRANCHES) ──

-- Allow OPERATIVE_ADVANCE_TRANCHE and OPERATIVE_DEBT_SETTLEMENT in service provider ledger
do $$
begin
  alter table public.service_provider_wallet_ledger drop constraint if exists service_provider_wallet_ledger_entry_type_check;
  alter table public.service_provider_wallet_ledger add constraint service_provider_wallet_ledger_entry_type_check
    check (entry_type in (
      'WELCOME_BONUS',
      'CONSTITUTIONAL_FEE_5_PERCENT',
      'TOPUP_SINPE_CONFIRMED',
      'TOPUP_MANUAL_CONFIRMED',
      'ADJUSTMENT_CREDIT',
      'FEE_REFUND',
      'OPERATIVE_ADVANCE_TRANCHE',
      'OPERATIVE_DEBT_SETTLEMENT'
    ));
exception when undefined_table then
  null;
end $$;

-- Allow OPERATIVE_ADVANCE_TRANCHE in ride_wallet_ledger
do $$
begin
  alter table public.ride_wallet_ledger drop constraint if exists ride_wallet_ledger_entry_type_check;
  alter table public.ride_wallet_ledger add constraint ride_wallet_ledger_entry_type_check
    check (entry_type in (
      'PROMOTIONAL_GRANT', 'TOP_UP_PENDING', 'TOP_UP_CONFIRMED',
      'COMMISSION_RESERVED', 'COMMISSION_CAPTURED', 'COMMISSION_RELEASED',
      'REFUND', 'ADJUSTMENT', 'OPERATIVE_ADVANCE_TRANCHE', 'OPERATIVE_DEBT_SETTLEMENT'
    ));
exception when undefined_table then
  null;
end $$;

-- Dedicated table for tracking operative advance credit line
create table if not exists public.provider_operative_advances (
    id uuid primary key default gen_random_uuid(),
    principal_id uuid not null references auth.users(id) on delete restrict,
    service_vertical text not null default 'ALL',
    amount_minor bigint not null default 3000 check (amount_minor = 3000),
    accumulated_debt_after_minor bigint not null check (accumulated_debt_after_minor >= 0),
    currency text not null default 'CRC' check (currency = 'CRC'),
    reference_code text not null unique,
    status text not null check (status in ('ACTIVE', 'SETTLED', 'DEFAULTED')) default 'ACTIVE',
    requested_at timestamptz not null default now(),
    settled_at timestamptz,
    settlement_reference text,
    enterprise_account_active boolean not null default false,
    metadata jsonb not null default '{}'::jsonb
);

create index if not exists idx_provider_operative_advances_principal
    on public.provider_operative_advances(principal_id, requested_at desc);

alter table public.provider_operative_advances enable row level security;
revoke all on public.provider_operative_advances from public, anon;
grant select on public.provider_operative_advances to authenticated;

drop policy if exists provider_operative_advances_owner_read on public.provider_operative_advances;
create policy provider_operative_advances_owner_read on public.provider_operative_advances
    for select to authenticated using (principal_id = auth.uid() or auth.role() = 'service_role');

-- Authoritative RPC to request an operative advance of ₡3,000 CRC
create or replace function public.provider_request_operative_advance_v1(
    p_service_vertical text default 'ALL'
)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare
    v_principal uuid := auth.uid();
    v_tranche_amount bigint := 3000;
    v_max_balance_threshold bigint := 400;
    v_current_balance bigint := 0;
    v_current_debt bigint := 0;
    v_new_debt bigint;
    v_new_balance bigint;
    v_ref_code text;
    v_advance_id uuid;
    v_enterprise_mandatory boolean := false;
begin
    if v_principal is null then
        raise exception 'UNAUTHENTICATED' using errcode='42501';
    end if;

    perform pg_advisory_xact_lock(hashtextextended('provider-finance:'||v_principal::text, 0));

    -- Check if enterprise settlement is mandatory
    select coalesce(bool_or(enterprise_account_active), false) into v_enterprise_mandatory
    from public.provider_operative_advances
    where principal_id = v_principal and status = 'ACTIVE';

    if v_enterprise_mandatory then
        raise exception 'OPERATIVE_SUSPENDED: Cuenta empresarial activa. Debes liquidar tu deuda acumulada para continuar operando.'
            using errcode='42501';
    end if;

    -- Ensure wallet exists
    insert into public.ride_wallets(driver_id, currency)
    values(v_principal, 'CRC') on conflict (driver_id) do nothing;

    -- Calculate current balance
    select coalesce(sum(case when direction='CREDIT' then amount_minor else -amount_minor end), 0)
    into v_current_balance
    from public.ride_wallet_ledger
    where driver_id = v_principal;

    if v_current_balance > v_max_balance_threshold then
        raise exception 'BALANCE_ABOVE_THRESHOLD: Solo puedes pedir prestado cuando tu saldo sea de ₡400 CRC o menos (Saldo actual: ₡% CRC)', v_current_balance
            using errcode='22023';
    end if;

    -- Calculate existing accumulated debt
    select coalesce(max(accumulated_debt_after_minor), 0)
    into v_current_debt
    from public.provider_operative_advances
    where principal_id = v_principal and status = 'ACTIVE';

    v_new_debt := v_current_debt + v_tranche_amount;
    v_ref_code := 'ADV-' || upper(substr(gen_random_uuid()::text, 1, 8));
    v_advance_id := gen_random_uuid();

    -- Insert advance record
    insert into public.provider_operative_advances (
        id, principal_id, service_vertical, amount_minor,
        accumulated_debt_after_minor, currency, reference_code,
        status, requested_at, metadata
    ) values (
        v_advance_id, v_principal, coalesce(p_service_vertical, 'ALL'), v_tranche_amount,
        v_new_debt, 'CRC', v_ref_code,
        'ACTIVE', now(),
        jsonb_build_object(
            'channel', 'ELYSIUM_OPERATIVE_CREDIT_LINE',
            'tranche_amount_crc', 3000,
            'balance_before_crc', v_current_balance
        )
    );

    -- Credit wallet in double-entry ledger
    insert into public.ride_wallet_ledger (
        driver_id, idempotency_key, entry_type, amount_minor,
        currency, direction, withdrawable, metadata
    ) values (
        v_principal,
        'operative-advance:' || v_advance_id::text,
        'OPERATIVE_ADVANCE_TRANCHE',
        v_tranche_amount,
        'CRC',
        'CREDIT',
        false,
        jsonb_build_object(
            'advance_id', v_advance_id,
            'reference_code', v_ref_code,
            'accumulated_debt_crc', v_new_debt,
            'entity', 'Elysium Network Corporation — Fideicomiso Operativo Transitorio'
        )
    );

    v_new_balance := v_current_balance + v_tranche_amount;

    return jsonb_build_object(
        'advance_id', v_advance_id,
        'reference_code', v_ref_code,
        'tranche_amount_crc', v_tranche_amount,
        'new_balance_crc', v_new_balance,
        'accumulated_debt_crc', v_new_debt,
        'status', 'APPROVED',
        'fiduciary_entity', 'Elysium Network Corporation — Fideicomiso Operativo Transitorio'
    );
end; $$;

revoke all on function public.provider_request_operative_advance_v1(text) from public, anon;
grant execute on function public.provider_request_operative_advance_v1(text) to authenticated;

-- Authoritative RPC to query debt summary
create or replace function public.provider_get_operative_debt_summary_v1(
    p_principal uuid default auth.uid()
)
returns jsonb language plpgsql stable security definer set search_path=public,pg_temp as $$
declare
    v_principal uuid := coalesce(p_principal, auth.uid());
    v_balance bigint := 0;
    v_debt bigint := 0;
    v_can_request boolean := false;
    v_suspended boolean := false;
begin
    if v_principal is null then
        raise exception 'UNAUTHENTICATED' using errcode='42501';
    end if;

    if v_principal <> auth.uid() and not public.meet_is_platform_owner() then
        raise exception 'ACCESS_DENIED' using errcode='42501';
    end if;

    select coalesce(sum(case when direction='CREDIT' then amount_minor else -amount_minor end), 0)
    into v_balance
    from public.ride_wallet_ledger
    where driver_id = v_principal;

    select coalesce(max(accumulated_debt_after_minor), 0)
    into v_debt
    from public.provider_operative_advances
    where principal_id = v_principal and status = 'ACTIVE';

    select coalesce(bool_or(enterprise_account_active), false) into v_suspended
    from public.provider_operative_advances
    where principal_id = v_principal and status = 'ACTIVE';

    v_can_request := (v_balance <= 400) and (not v_suspended);

    return jsonb_build_object(
        'principal_id', v_principal,
        'current_balance_crc', v_balance,
        'accumulated_debt_crc', v_debt,
        'min_threshold_crc', 400,
        'tranche_amount_crc', 3000,
        'can_request_advance', v_can_request,
        'is_operative_suspended', v_suspended,
        'fiduciary_entity', 'Elysium Network Corporation — Fideicomiso Operativo Transitorio'
    );
end; $$;

revoke all on function public.provider_get_operative_debt_summary_v1(uuid) from public, anon;
grant execute on function public.provider_get_operative_debt_summary_v1(uuid) to authenticated;

-- ── 4. FLEET CHAT MESSAGES STORAGE & MESH CLOUD RECONCILIATION ──

create table if not exists public.fleet_chat_messages (
    id text primary key,
    business_id text not null,
    sender_id text not null,
    receiver_id text not null,
    message_text text,
    message_type text not null check (message_type in ('TEXT', 'AUDIO', 'FILE', 'IMAGE', 'DTC_ALERT', 'CALL', 'VIDEO_CALL')),
    file_remote_url text,
    duration_seconds integer not null default 0,
    transport_route text not null default 'INTERNET',
    created_at timestamptz not null default now(),
    status text not null default 'DELIVERED'
);

create index if not exists idx_fleet_chat_messages_business_time
    on public.fleet_chat_messages(business_id, created_at desc);

alter table public.fleet_chat_messages enable row level security;
revoke all on public.fleet_chat_messages from public, anon;
grant select, insert on public.fleet_chat_messages to authenticated;

drop policy if exists fleet_chat_messages_authenticated_select on public.fleet_chat_messages;
create policy fleet_chat_messages_authenticated_select on public.fleet_chat_messages
    for select to authenticated using (true);

drop policy if exists fleet_chat_messages_authenticated_insert on public.fleet_chat_messages;
create policy fleet_chat_messages_authenticated_insert on public.fleet_chat_messages
    for insert to authenticated with check (auth.uid() is not null);

commit;
