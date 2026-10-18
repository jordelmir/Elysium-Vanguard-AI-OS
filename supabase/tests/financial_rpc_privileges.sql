-- supabase/tests/financial_rpc_privileges.sql
-- CI gate: verify financial RPCs are not callable by anonymous/public roles.
-- Run via: psql "$DB_URL" -v ON_ERROR_STOP=1 -f supabase/tests/financial_rpc_privileges.sql

DO $$
DECLARE
    v_failures text[] := '{}';
    v_rec record;
    v_func_count integer := 0;
    v_exists_count integer := 0;
    v_target_funcs text[] := ARRAY[
        'ride_submit_wallet_topup_v1',
        'ride_wallet_ensure_starter_credit_v1',
        'ride_owner_wallet_topup_queue_v1',
        'ride_owner_decide_wallet_topup_v1',
        'ride_wallet_balance_v1',
        'ride_driver_has_offer_balance',
        'ride_offer_wallet_guard',
        'ride_driver_wallet_credit_v1',
        'sinpe_ingest_email_receipt_v1',
        'sinpe_claim_receipt_v1',
        'mobility_generate_quote',
        'mobility_authorize_payment',
        'mobility_authorize_quote_payment',
        'mobility_post_ledger_transaction',
        'mobility_settle_trip',
        'mobility_confirm_tip_capture',
        'mobility_settle_trip_tip'
    ];
    v_target text;
BEGIN
    FOREACH v_target IN ARRAY v_target_funcs LOOP
        FOR v_rec IN
            SELECT p.proname AS func_name,
                   CASE WHEN p.proacl IS NULL THEN '{}'::text[]
                        ELSE ARRAY(SELECT ac.grantee::regrole::text
                                   FROM aclexplode(p.proacl) ac
                                   WHERE ac.privilege_type = 'EXECUTE')
                   END AS granted_to
            FROM pg_proc p
            JOIN pg_namespace n ON p.pronamespace = n.oid
            WHERE n.nspname = 'public'
              AND p.proname = v_target
        LOOP
            v_exists_count := v_exists_count + 1;
            IF 'anon' = ANY(v_rec.granted_to) THEN
                RAISE NOTICE 'FAIL: % grants EXECUTE to anon', v_rec.func_name;
                v_failures := array_append(v_failures, format('%s->anon', v_rec.func_name));
            END IF;
            IF 'public' = ANY(v_rec.granted_to) THEN
                RAISE NOTICE 'FAIL: % grants EXECUTE to public', v_rec.func_name;
                v_failures := array_append(v_failures, format('%s->public', v_rec.func_name));
            END IF;
        END LOOP;
    END LOOP;

    RAISE NOTICE 'Checked % functions (% exist)', array_length(v_target_funcs, 1), v_exists_count;

    -- Verify RLS on critical tables (only if they exist)
    IF EXISTS (
        SELECT 1 FROM pg_class c
        JOIN pg_namespace n ON c.relnamespace = n.oid
        WHERE n.nspname = 'public' AND c.relname = 'sinpe_incoming_receipts'
    ) AND NOT EXISTS (
        SELECT 1 FROM pg_class c
        JOIN pg_namespace n ON c.relnamespace = n.oid
        WHERE n.nspname = 'public' AND c.relname = 'sinpe_incoming_receipts'
          AND c.relrowsecurity = true
    ) THEN
        v_failures := array_append(v_failures, 'sinpe_incoming_receipts_missing_RLS');
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_class c
        JOIN pg_namespace n ON c.relnamespace = n.oid
        WHERE n.nspname = 'public' AND c.relname = 'ride_wallet_ledger'
    ) AND NOT EXISTS (
        SELECT 1 FROM pg_class c
        JOIN pg_namespace n ON c.relnamespace = n.oid
        WHERE n.nspname = 'public' AND c.relname = 'ride_wallet_ledger'
          AND c.relrowsecurity = true
    ) THEN
        v_failures := array_append(v_failures, 'ride_wallet_ledger_missing_RLS');
    END IF;

    -- Verify RLS is enabled on all base tables in the public schema (Lintel / Supabase Security Advisory)
    FOR v_rec IN
        SELECT c.relname
        FROM pg_class c
        JOIN pg_namespace n ON c.relnamespace = n.oid
        WHERE n.nspname = 'public'
          AND c.relkind = 'r'
          AND c.relrowsecurity = false
        ORDER BY c.relname
    LOOP
        v_failures := array_append(v_failures, format('%s_missing_RLS', v_rec.relname));
        RAISE NOTICE 'FAIL: Table public.% has RLS disabled', v_rec.relname;
    END LOOP;

    IF array_length(v_failures, 1) IS NULL OR array_length(v_failures, 1) = 0 THEN
        RAISE NOTICE 'RESULT: PASS';
    ELSE
        RAISE EXCEPTION 'RESULT: FAIL — %', array_to_string(v_failures, ', ');
    END IF;
END $$;
