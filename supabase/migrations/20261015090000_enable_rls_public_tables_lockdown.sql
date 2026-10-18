-- ══════════════════════════════════════════════════════════════════════════════
-- ELYSIUM SECURITY HARDENING: ROW LEVEL SECURITY AUDIT & LOCKDOWN (27 PUBLIC TABLES)
-- Migration: 20261015090000_enable_rls_public_tables_lockdown.sql
-- ══════════════════════════════════════════════════════════════════════════════
-- Fixes security advisory reported by Lintel (Ahmed Sozzer):
-- 26 tables in the public schema were discovered with RLS disabled across legacy migrations,
-- plus 1 subsequent deduplication table. Unprotected public tables can be read/written
-- via PostgREST by any holder of the anon public key.
--
-- This migration:
-- 1. Enables Row Level Security on all 27 tables.
-- 2. Revokes direct write/mutation permissions from anon and authenticated roles
--    where operations must be routed strictly through security-definer RPCs.
-- 3. Implements strict, least-privilege SELECT and mutation policies based on
--    actor ownership, trip participation, or platform authority (meet_is_platform_owner()).
-- ══════════════════════════════════════════════════════════════════════════════

BEGIN;

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. BILLING & COMMERCE CATALOG
-- ─────────────────────────────────────────────────────────────────────────────

-- 1.1 billing_products (Catalog of in-app products and subscriptions)
ALTER TABLE IF EXISTS public.billing_products ENABLE ROW LEVEL SECURITY;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON public.billing_products FROM anon, authenticated;
DROP POLICY IF EXISTS billing_products_select_active ON public.billing_products;
CREATE POLICY billing_products_select_active ON public.billing_products
    FOR SELECT TO anon, authenticated
    USING (is_active = true OR public.meet_is_platform_owner());


-- ─────────────────────────────────────────────────────────────────────────────
-- 2. MOBILITY MARKET AUTHORITY & PARTICIPANT ELIGIBILITY
-- ─────────────────────────────────────────────────────────────────────────────

-- 2.1 mobility_markets (Geographic markets and configuration)
ALTER TABLE IF EXISTS public.mobility_markets ENABLE ROW LEVEL SECURITY;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON public.mobility_markets FROM anon, authenticated;
DROP POLICY IF EXISTS mobility_markets_select_active ON public.mobility_markets;
CREATE POLICY mobility_markets_select_active ON public.mobility_markets
    FOR SELECT TO anon, authenticated
    USING (active = true OR public.meet_is_platform_owner());

-- 2.2 mobility_service_categories (Service tiers: STD, COMFORT, XL, TOW, etc.)
ALTER TABLE IF EXISTS public.mobility_service_categories ENABLE ROW LEVEL SECURITY;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON public.mobility_service_categories FROM anon, authenticated;
DROP POLICY IF EXISTS mobility_service_categories_select_active ON public.mobility_service_categories;
CREATE POLICY mobility_service_categories_select_active ON public.mobility_service_categories
    FOR SELECT TO anon, authenticated
    USING (active = true OR public.meet_is_platform_owner());

-- 2.3 mobility_vehicles (Registered driver/owner vehicles)
ALTER TABLE IF EXISTS public.mobility_vehicles ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.mobility_vehicles FROM anon;
REVOKE DELETE, TRUNCATE ON public.mobility_vehicles FROM authenticated;

DROP POLICY IF EXISTS mobility_vehicles_owner_and_participants_select ON public.mobility_vehicles;
CREATE POLICY mobility_vehicles_owner_and_participants_select ON public.mobility_vehicles
    FOR SELECT TO authenticated
    USING (
        owner_id = (SELECT auth.uid())
        OR EXISTS (
            SELECT 1 FROM public.driver_vehicle_authorizations dva
            WHERE dva.vehicle_id = mobility_vehicles.vehicle_id
              AND dva.driver_id = (SELECT auth.uid())
              AND dva.is_authorized
              AND dva.active
        )
        OR EXISTS (
            SELECT 1 FROM public.trips t
            WHERE t.vehicle_id = mobility_vehicles.vehicle_id
              AND t.rider_id = (SELECT auth.uid())
        )
        OR public.meet_is_platform_owner()
    );

DROP POLICY IF EXISTS mobility_vehicles_owner_insert ON public.mobility_vehicles;
CREATE POLICY mobility_vehicles_owner_insert ON public.mobility_vehicles
    FOR INSERT TO authenticated
    WITH CHECK (owner_id = (SELECT auth.uid()) OR public.meet_is_platform_owner());

DROP POLICY IF EXISTS mobility_vehicles_owner_update ON public.mobility_vehicles;
CREATE POLICY mobility_vehicles_owner_update ON public.mobility_vehicles
    FOR UPDATE TO authenticated
    USING (owner_id = (SELECT auth.uid()) OR public.meet_is_platform_owner())
    WITH CHECK (owner_id = (SELECT auth.uid()) OR public.meet_is_platform_owner());

-- 2.4 driver_market_eligibility (Driver credentials & background check by market)
ALTER TABLE IF EXISTS public.driver_market_eligibility ENABLE ROW LEVEL SECURITY;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON public.driver_market_eligibility FROM anon, authenticated;
DROP POLICY IF EXISTS driver_market_eligibility_driver_select ON public.driver_market_eligibility;
CREATE POLICY driver_market_eligibility_driver_select ON public.driver_market_eligibility
    FOR SELECT TO authenticated
    USING (driver_id = (SELECT auth.uid()) OR public.meet_is_platform_owner());

-- 2.5 driver_vehicle_authorizations (Owner authorizations for drivers to operate vehicles)
ALTER TABLE IF EXISTS public.driver_vehicle_authorizations ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.driver_vehicle_authorizations FROM anon;
REVOKE TRUNCATE ON public.driver_vehicle_authorizations FROM authenticated;

DROP POLICY IF EXISTS driver_vehicle_auth_select ON public.driver_vehicle_authorizations;
CREATE POLICY driver_vehicle_auth_select ON public.driver_vehicle_authorizations
    FOR SELECT TO authenticated
    USING (
        driver_id = (SELECT auth.uid())
        OR EXISTS (
            SELECT 1 FROM public.mobility_vehicles mv
            WHERE mv.vehicle_id = driver_vehicle_authorizations.vehicle_id
              AND mv.owner_id = (SELECT auth.uid())
        )
        OR public.meet_is_platform_owner()
    );

DROP POLICY IF EXISTS driver_vehicle_auth_owner_manage ON public.driver_vehicle_authorizations;
CREATE POLICY driver_vehicle_auth_owner_manage ON public.driver_vehicle_authorizations
    FOR ALL TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.mobility_vehicles mv
            WHERE mv.vehicle_id = driver_vehicle_authorizations.vehicle_id
              AND mv.owner_id = (SELECT auth.uid())
        )
        OR public.meet_is_platform_owner()
    )
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.mobility_vehicles mv
            WHERE mv.vehicle_id = driver_vehicle_authorizations.vehicle_id
              AND mv.owner_id = (SELECT auth.uid())
        )
        OR public.meet_is_platform_owner()
    );

-- 2.6 mobility_pair_blocks (Mutual rider/driver blocking for safety)
ALTER TABLE IF EXISTS public.mobility_pair_blocks ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.mobility_pair_blocks FROM anon;
REVOKE DELETE, TRUNCATE ON public.mobility_pair_blocks FROM authenticated;

DROP POLICY IF EXISTS mobility_pair_blocks_select ON public.mobility_pair_blocks;
CREATE POLICY mobility_pair_blocks_select ON public.mobility_pair_blocks
    FOR SELECT TO authenticated
    USING (blocker_id = (SELECT auth.uid()) OR public.meet_is_platform_owner());

DROP POLICY IF EXISTS mobility_pair_blocks_insert ON public.mobility_pair_blocks;
CREATE POLICY mobility_pair_blocks_insert ON public.mobility_pair_blocks
    FOR INSERT TO authenticated
    WITH CHECK (blocker_id = (SELECT auth.uid()) OR public.meet_is_platform_owner());

DROP POLICY IF EXISTS mobility_pair_blocks_update ON public.mobility_pair_blocks;
CREATE POLICY mobility_pair_blocks_update ON public.mobility_pair_blocks
    FOR UPDATE TO authenticated
    USING (blocker_id = (SELECT auth.uid()) OR public.meet_is_platform_owner())
    WITH CHECK (blocker_id = (SELECT auth.uid()) OR public.meet_is_platform_owner());

-- 2.7 dispatch_offers (Auto-dispatch job offers to drivers)
ALTER TABLE IF EXISTS public.dispatch_offers ENABLE ROW LEVEL SECURITY;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON public.dispatch_offers FROM anon, authenticated;
DROP POLICY IF EXISTS dispatch_offers_driver_select ON public.dispatch_offers;
CREATE POLICY dispatch_offers_driver_select ON public.dispatch_offers
    FOR SELECT TO authenticated
    USING (driver_id = (SELECT auth.uid()) OR public.meet_is_platform_owner());

-- 2.8 ride_driver_offers (Driver marketplace counter-offers and bids)
ALTER TABLE IF EXISTS public.ride_driver_offers ENABLE ROW LEVEL SECURITY;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON public.ride_driver_offers FROM anon, authenticated;
DROP POLICY IF EXISTS ride_driver_offers_participant_select ON public.ride_driver_offers;
CREATE POLICY ride_driver_offers_participant_select ON public.ride_driver_offers
    FOR SELECT TO authenticated
    USING (
        driver_id = (SELECT auth.uid())
        OR EXISTS (
            SELECT 1 FROM public.ride_requests rr
            WHERE (rr.ride_request_id = ride_driver_offers.ride_request_id OR rr.id = ride_driver_offers.ride_request_id)
              AND (rr.rider_id = (SELECT auth.uid()) OR rr.passenger_id = (SELECT auth.uid()))
        )
        OR public.meet_is_platform_owner()
    );

-- 2.9 mobility_command_receipts (Idempotency and audit receipts)
ALTER TABLE IF EXISTS public.mobility_command_receipts ENABLE ROW LEVEL SECURITY;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON public.mobility_command_receipts FROM anon, authenticated;
DROP POLICY IF EXISTS mobility_command_receipts_actor_select ON public.mobility_command_receipts;
CREATE POLICY mobility_command_receipts_actor_select ON public.mobility_command_receipts
    FOR SELECT TO authenticated
    USING (actor_id = (SELECT auth.uid()) OR public.meet_is_platform_owner());

-- 2.10 mobility_realtime_memberships (Realtime channel subscription authorizations)
ALTER TABLE IF EXISTS public.mobility_realtime_memberships ENABLE ROW LEVEL SECURITY;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON public.mobility_realtime_memberships FROM anon, authenticated;
DROP POLICY IF EXISTS mobility_realtime_memberships_user_select ON public.mobility_realtime_memberships;
CREATE POLICY mobility_realtime_memberships_user_select ON public.mobility_realtime_memberships
    FOR SELECT TO authenticated
    USING (user_id = (SELECT auth.uid()) OR public.meet_is_platform_owner());


-- ─────────────────────────────────────────────────────────────────────────────
-- 3. FINANCIAL AUTHORITY & PRICING POLICIES
-- ─────────────────────────────────────────────────────────────────────────────

-- 3.1 ride_quotes (Pre-calculated binding trip estimates)
ALTER TABLE IF EXISTS public.ride_quotes ENABLE ROW LEVEL SECURITY;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON public.ride_quotes FROM anon, authenticated;
DROP POLICY IF EXISTS ride_quotes_participant_select ON public.ride_quotes;
CREATE POLICY ride_quotes_participant_select ON public.ride_quotes
    FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.ride_requests rr
            WHERE (rr.ride_request_id = ride_quotes.ride_request_id OR rr.id = ride_quotes.ride_request_id)
              AND (rr.rider_id = (SELECT auth.uid()) OR rr.passenger_id = (SELECT auth.uid()))
        )
        OR EXISTS (
            SELECT 1 FROM public.trips t
            WHERE t.quote_id = ride_quotes.quote_id
              AND (t.rider_id = (SELECT auth.uid()) OR t.driver_id = (SELECT auth.uid()))
        )
        OR public.meet_is_platform_owner()
    );

-- 3.2 mobility_pricing_policies (Tariff and commission definitions)
ALTER TABLE IF EXISTS public.mobility_pricing_policies ENABLE ROW LEVEL SECURITY;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON public.mobility_pricing_policies FROM anon, authenticated;
DROP POLICY IF EXISTS mobility_pricing_policies_select_active ON public.mobility_pricing_policies;
CREATE POLICY mobility_pricing_policies_select_active ON public.mobility_pricing_policies
    FOR SELECT TO anon, authenticated
    USING (active = true OR public.meet_is_platform_owner());

-- 3.3 ride_wallet_policy (Driver onboarding credit and commission rate)
ALTER TABLE IF EXISTS public.ride_wallet_policy ENABLE ROW LEVEL SECURITY;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON public.ride_wallet_policy FROM anon, authenticated;
DROP POLICY IF EXISTS ride_wallet_policy_select ON public.ride_wallet_policy;
CREATE POLICY ride_wallet_policy_select ON public.ride_wallet_policy
    FOR SELECT TO authenticated
    USING (policy_id = true OR public.meet_is_platform_owner());


-- ─────────────────────────────────────────────────────────────────────────────
-- 4. MOBILITY SAFETY & RESERVATIONS
-- ─────────────────────────────────────────────────────────────────────────────

-- 4.1 masked_communication_sessions (Virtual proxy numbers between rider and driver)
ALTER TABLE IF EXISTS public.masked_communication_sessions ENABLE ROW LEVEL SECURITY;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON public.masked_communication_sessions FROM anon, authenticated;
DROP POLICY IF EXISTS masked_comm_sessions_participant_select ON public.masked_communication_sessions;
CREATE POLICY masked_comm_sessions_participant_select ON public.masked_communication_sessions
    FOR SELECT TO authenticated
    USING (
        rider_id = (SELECT auth.uid())
        OR driver_id = (SELECT auth.uid())
        OR public.meet_is_platform_owner()
    );

-- 4.2 mobility_risk_zones (Geofenced hazard / high-risk security zones)
ALTER TABLE IF EXISTS public.mobility_risk_zones ENABLE ROW LEVEL SECURITY;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON public.mobility_risk_zones FROM anon, authenticated;
DROP POLICY IF EXISTS mobility_risk_zones_select_active ON public.mobility_risk_zones;
CREATE POLICY mobility_risk_zones_select_active ON public.mobility_risk_zones
    FOR SELECT TO anon, authenticated
    USING (active = true OR public.meet_is_platform_owner());

-- 4.3 route_deviation_logs (GPS corridor off-route deviation logs)
ALTER TABLE IF EXISTS public.route_deviation_logs ENABLE ROW LEVEL SECURITY;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON public.route_deviation_logs FROM anon, authenticated;
DROP POLICY IF EXISTS route_deviation_logs_participant_select ON public.route_deviation_logs;
CREATE POLICY route_deviation_logs_participant_select ON public.route_deviation_logs
    FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.trips t
            WHERE t.trip_id = route_deviation_logs.trip_id
              AND (t.rider_id = (SELECT auth.uid()) OR t.driver_id = (SELECT auth.uid()))
        )
        OR public.meet_is_platform_owner()
    );

-- 4.4 safety_emergency_events (SOS triggers, crash alerts, panic events)
ALTER TABLE IF EXISTS public.safety_emergency_events ENABLE ROW LEVEL SECURITY;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON public.safety_emergency_events FROM anon, authenticated;
DROP POLICY IF EXISTS safety_emergency_events_participant_select ON public.safety_emergency_events;
CREATE POLICY safety_emergency_events_participant_select ON public.safety_emergency_events
    FOR SELECT TO authenticated
    USING (
        triggered_by = (SELECT auth.uid())
        OR EXISTS (
            SELECT 1 FROM public.trips t
            WHERE t.trip_id = safety_emergency_events.trip_id
              AND (t.rider_id = (SELECT auth.uid()) OR t.driver_id = (SELECT auth.uid()))
        )
        OR public.meet_is_platform_owner()
    );

-- 4.5 scheduled_ride_policies (Advance reservation rules and lead times)
ALTER TABLE IF EXISTS public.scheduled_ride_policies ENABLE ROW LEVEL SECURITY;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON public.scheduled_ride_policies FROM anon, authenticated;
DROP POLICY IF EXISTS scheduled_ride_policies_select ON public.scheduled_ride_policies;
CREATE POLICY scheduled_ride_policies_select ON public.scheduled_ride_policies
    FOR SELECT TO anon, authenticated
    USING (true);

-- 4.6 scheduled_ride_reservations (Passenger advance scheduled rides)
ALTER TABLE IF EXISTS public.scheduled_ride_reservations ENABLE ROW LEVEL SECURITY;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON public.scheduled_ride_reservations FROM anon, authenticated;
DROP POLICY IF EXISTS scheduled_ride_reservations_participant_select ON public.scheduled_ride_reservations;
CREATE POLICY scheduled_ride_reservations_participant_select ON public.scheduled_ride_reservations
    FOR SELECT TO authenticated
    USING (
        rider_id = (SELECT auth.uid())
        OR EXISTS (
            SELECT 1 FROM public.ride_requests rr
            WHERE (rr.ride_request_id = scheduled_ride_reservations.ride_request_id OR rr.id = scheduled_ride_reservations.ride_request_id)
              AND (rr.assigned_driver_id = (SELECT auth.uid()))
        )
        OR public.meet_is_platform_owner()
    );


-- ─────────────────────────────────────────────────────────────────────────────
-- 5. COMMUNICATIONS, REPUTATION & SURGE
-- ─────────────────────────────────────────────────────────────────────────────

-- 5.1 lost_item_cases (Lost property claims between rider and driver)
ALTER TABLE IF EXISTS public.lost_item_cases ENABLE ROW LEVEL SECURITY;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON public.lost_item_cases FROM anon, authenticated;
DROP POLICY IF EXISTS lost_item_cases_participant_select ON public.lost_item_cases;
CREATE POLICY lost_item_cases_participant_select ON public.lost_item_cases
    FOR SELECT TO authenticated
    USING (
        rider_id = (SELECT auth.uid())
        OR driver_id = (SELECT auth.uid())
        OR public.meet_is_platform_owner()
    );

-- 5.2 market_policy_configurations (Dynamic search radius and surge limits)
ALTER TABLE IF EXISTS public.market_policy_configurations ENABLE ROW LEVEL SECURITY;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON public.market_policy_configurations FROM anon, authenticated;
DROP POLICY IF EXISTS market_policy_configurations_select ON public.market_policy_configurations;
CREATE POLICY market_policy_configurations_select ON public.market_policy_configurations
    FOR SELECT TO anon, authenticated
    USING (true);

-- 5.3 support_cases (Customer care and dispute tickets)
ALTER TABLE IF EXISTS public.support_cases ENABLE ROW LEVEL SECURITY;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON public.support_cases FROM anon, authenticated;
DROP POLICY IF EXISTS support_cases_owner_select ON public.support_cases;
CREATE POLICY support_cases_owner_select ON public.support_cases
    FOR SELECT TO authenticated
    USING (user_id = (SELECT auth.uid()) OR public.meet_is_platform_owner());

-- 5.4 trip_messages (In-trip encrypted messaging between rider and driver)
ALTER TABLE IF EXISTS public.trip_messages ENABLE ROW LEVEL SECURITY;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON public.trip_messages FROM anon, authenticated;
DROP POLICY IF EXISTS trip_messages_participant_select ON public.trip_messages;
CREATE POLICY trip_messages_participant_select ON public.trip_messages
    FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.trips t
            WHERE t.trip_id = trip_messages.trip_id
              AND (t.rider_id = (SELECT auth.uid()) OR t.driver_id = (SELECT auth.uid()))
        )
        OR public.meet_is_platform_owner()
    );

-- 5.5 trip_ratings (Post-trip reviews and reputation scores)
ALTER TABLE IF EXISTS public.trip_ratings ENABLE ROW LEVEL SECURITY;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON public.trip_ratings FROM anon, authenticated;
DROP POLICY IF EXISTS trip_ratings_participant_select ON public.trip_ratings;
CREATE POLICY trip_ratings_participant_select ON public.trip_ratings
    FOR SELECT TO authenticated
    USING (
        reviewer_id = (SELECT auth.uid())
        OR subject_id = (SELECT auth.uid())
        OR public.meet_is_platform_owner()
    );


-- ─────────────────────────────────────────────────────────────────────────────
-- 6. CANONICAL OPERATIONS & CONCURRENCY
-- ─────────────────────────────────────────────────────────────────────────────

-- 6.1 mobility_trip_state_transitions (State machine valid transition graph)
ALTER TABLE IF EXISTS public.mobility_trip_state_transitions ENABLE ROW LEVEL SECURITY;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON public.mobility_trip_state_transitions FROM anon, authenticated;
DROP POLICY IF EXISTS mobility_trip_state_transitions_select ON public.mobility_trip_state_transitions;
CREATE POLICY mobility_trip_state_transitions_select ON public.mobility_trip_state_transitions
    FOR SELECT TO anon, authenticated
    USING (true);

-- 6.2 ride_command_dedup (Server-authoritative idempotent command ledger)
ALTER TABLE IF EXISTS public.ride_command_dedup ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.ride_command_dedup FROM anon, authenticated;
DROP POLICY IF EXISTS ride_command_dedup_actor_select ON public.ride_command_dedup;
CREATE POLICY ride_command_dedup_actor_select ON public.ride_command_dedup
    FOR SELECT TO authenticated
    USING (actor_id = (SELECT auth.uid()) OR public.meet_is_platform_owner());

COMMIT;
