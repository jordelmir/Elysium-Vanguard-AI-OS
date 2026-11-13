import { createClient } from "npm:@supabase/supabase-js@2";

const encoder = new TextEncoder();
const MAX_BODY_BYTES = 4_096;
const JOIN_WINDOW_MS = 90_000;
const SESSION_WINDOW_MS = 4 * 60 * 60 * 1_000;
const MAX_SESSIONS_PER_HOUR = 20;

type SessionAction = "create" | "join" | "close" | "refresh-turn";
type IceServer = { urls: string[]; username?: string; credential?: string };

function json(status: number, body: Record<string, unknown>): Response {
  return Response.json(body, {
    status,
    headers: {
      "cache-control": "no-store, max-age=0",
      "pragma": "no-cache",
      "x-content-type-options": "nosniff",
    },
  });
}

function requiredEnv(name: string): string {
  const value = Deno.env.get(name)?.trim();
  if (!value) throw new Error(`Missing server configuration: ${name}`);
  return value;
}

function toHex(bytes: Uint8Array): string {
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("");
}

function toBase64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/u, "");
}

function fromBase64Url(value: string): Uint8Array | null {
  if (!/^[A-Za-z0-9_-]{43}$/u.test(value)) return null;
  const normalized = value.replaceAll("-", "+").replaceAll("_", "/") + "=";
  try {
    return Uint8Array.from(atob(normalized), (character) => character.charCodeAt(0));
  } catch {
    return null;
  }
}

async function sha256Hex(bytes: Uint8Array): Promise<string> {
  return toHex(new Uint8Array(await crypto.subtle.digest("SHA-256", bytes)));
}

async function currentUser(request: Request, admin: ReturnType<typeof createClient>) {
  const authorization = request.headers.get("authorization") ?? "";
  const match = /^Bearer ([A-Za-z0-9._~-]+)$/u.exec(authorization);
  if (!match) return null;
  const { data, error } = await admin.auth.getUser(match[1]);
  if (error || !data.user) return null;
  return data.user;
}

function parseIceUrls(name: string): string[] {
  return (Deno.env.get(name) ?? "")
    .split(",")
    .map((url) => url.trim())
    .filter((url) => /^(stun|stuns|turn|turns):[^\s]+$/u.test(url));
}

async function createTurnCredential(userId: string): Promise<IceServer[]> {
  const urls = parseIceUrls("NEXUS_TURN_URLS");
  const sharedSecret = Deno.env.get("NEXUS_TURN_SHARED_SECRET")?.trim();
  if (urls.length === 0 || !sharedSecret) return [];

  // The longest signaling session lasts four hours. TURN credentials are
  // scoped to that same short lifetime, plus a small reconnect margin.
  const expiresAt = Math.floor(Date.now() / 1_000) + 4 * 60 * 60 + 10 * 60;
  const username = `${expiresAt}:${userId.slice(0, 12)}`;
  const key = await crypto.subtle.importKey(
    "raw",
    encoder.encode(sharedSecret),
    { name: "HMAC", hash: "SHA-1" },
    false,
    ["sign"],
  );
  const signature = new Uint8Array(await crypto.subtle.sign("HMAC", key, encoder.encode(username)));
  let binary = "";
  for (const byte of signature) binary += String.fromCharCode(byte);
  const credential = btoa(binary);
  return [{ urls, username, credential }];
}

async function iceConfiguration(userId: string) {
  const configuredStun = parseIceUrls("NEXUS_STUN_URLS");
  const turn = await createTurnCredential(userId);
  const iceServers: IceServer[] = [
    ...configuredStun.map((url) => ({ urls: [url] })),
    ...turn,
  ];
  return { iceServers, relayAvailable: turn.length > 0 };
}

function validSessionId(value: unknown): value is string {
  return typeof value === "string" &&
    /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/iu.test(value);
}

function validRequestId(value: unknown): value is string {
  return typeof value === "string" &&
    /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/iu.test(value);
}

function validAction(value: unknown): value is SessionAction {
  return value === "create" || value === "join" || value === "close" || value === "refresh-turn";
}

Deno.serve(async (request) => {
  if (request.method !== "POST") return json(405, { error: "method_not_allowed" });
  const length = Number(request.headers.get("content-length") ?? "0");
  if (Number.isFinite(length) && length > MAX_BODY_BYTES) return json(413, { error: "request_too_large" });

  let body: Record<string, unknown>;
  try {
    const bytes = new Uint8Array(await request.arrayBuffer());
    if (bytes.byteLength > MAX_BODY_BYTES) return json(413, { error: "request_too_large" });
    body = JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes));
    if (body === null || typeof body !== "object" || Array.isArray(body)) throw new Error("invalid_body");
  } catch {
    return json(400, { error: "invalid_json" });
  }

  if (!validAction(body.action)) return json(400, { error: "invalid_action" });

  let admin: ReturnType<typeof createClient>;
  try {
    admin = createClient(requiredEnv("SUPABASE_URL"), requiredEnv("SUPABASE_SERVICE_ROLE_KEY"), {
      auth: { persistSession: false, autoRefreshToken: false },
    });
  } catch {
    return json(503, { error: "service_unavailable" });
  }

  try {
    const user = await currentUser(request, admin);
    if (!user) return json(401, { error: "authentication_required" });
    if (user.is_anonymous !== true) return json(403, { error: "temporary_session_required" });

    if (body.action === "create") {
      if (!validRequestId(body.requestId)) return json(400, { error: "invalid_request_id" });
      if (typeof body.pairingSecret !== "string") return json(400, { error: "invalid_pairing_secret" });
      const secretBytes = fromBase64Url(body.pairingSecret);
      if (!secretBytes) return json(400, { error: "invalid_pairing_secret" });
      const { error: pruneError } = await admin.rpc("nexus_prune_expired_sessions");
      if (pruneError) return json(503, { error: "session_service_unavailable" });
      const pairingSecretSha256 = await sha256Hex(secretBytes);
      const { data: prior, error: priorError } = await admin
        .from("nexus_signaling_sessions")
        .select("id, pairing_secret_sha256, join_expires_at, expires_at, closed_at, guest_user_id")
        .eq("host_user_id", user.id)
        .eq("creation_request_id", body.requestId)
        .maybeSingle();
      if (priorError) return json(503, { error: "session_service_unavailable" });
      if (prior) {
        if (prior.pairing_secret_sha256 !== pairingSecretSha256) return json(409, { error: "idempotency_conflict" });
        if (prior.closed_at || Date.parse(prior.expires_at) <= Date.now()) return json(410, { error: "pairing_expired" });
        const ice = await iceConfiguration(user.id);
        return json(200, {
          sessionId: prior.id,
          topic: `nexus-session:${prior.id}`,
          pairingSecret: body.pairingSecret,
          joinExpiresAt: prior.join_expires_at,
          expiresAt: prior.expires_at,
          paired: prior.guest_user_id !== null,
          iceServers: ice.iceServers,
          relayAvailable: ice.relayAvailable,
        });
      }

      const oneHourAgo = new Date(Date.now() - 60 * 60 * 1_000).toISOString();
      const { count, error: countError } = await admin
        .from("nexus_signaling_sessions")
        .select("id", { count: "exact", head: true })
        .eq("host_user_id", user.id)
        .gte("created_at", oneHourAgo);
      if (countError) return json(503, { error: "session_service_unavailable" });
      if ((count ?? 0) >= MAX_SESSIONS_PER_HOUR) return json(429, { error: "session_rate_limited" });

      const id = crypto.randomUUID();
      const now = Date.now();
      const joinExpiresAt = new Date(now + JOIN_WINDOW_MS).toISOString();
      const { error } = await admin.from("nexus_signaling_sessions").insert({
        id,
        host_user_id: user.id,
        creation_request_id: body.requestId,
        pairing_secret_sha256: pairingSecretSha256,
        join_expires_at: joinExpiresAt,
        expires_at: joinExpiresAt,
      });
      if (error?.code === "23505") {
        const { data: existing, error: lookupError } = await admin
          .from("nexus_signaling_sessions")
          .select("id, pairing_secret_sha256, join_expires_at, expires_at, closed_at, guest_user_id")
          .eq("host_user_id", user.id)
          .eq("creation_request_id", body.requestId)
          .maybeSingle();
        if (lookupError) return json(503, { error: "session_service_unavailable" });
        if (!existing || existing.pairing_secret_sha256 !== pairingSecretSha256) {
          return json(409, { error: "idempotency_conflict" });
        }
        if (existing.closed_at || Date.parse(existing.expires_at) <= Date.now()) {
          return json(410, { error: "pairing_expired" });
        }
        const ice = await iceConfiguration(user.id);
        return json(200, {
          sessionId: existing.id,
          topic: `nexus-session:${existing.id}`,
          pairingSecret: body.pairingSecret,
          joinExpiresAt: existing.join_expires_at,
          expiresAt: existing.expires_at,
          paired: existing.guest_user_id !== null,
          iceServers: ice.iceServers,
          relayAvailable: ice.relayAvailable,
        });
      }
      if (error) return json(503, { error: "session_service_unavailable" });

      const ice = await iceConfiguration(user.id);
      // Keep the pairing secret only in this response and the user's QR/code.
      return json(201, {
        sessionId: id,
        topic: `nexus-session:${id}`,
        pairingSecret: body.pairingSecret,
        joinExpiresAt,
        iceServers: ice.iceServers,
        relayAvailable: ice.relayAvailable,
      });
    }

    if (!validSessionId(body.sessionId)) return json(400, { error: "invalid_session_id" });
    const sessionId = body.sessionId;

    if (body.action === "join") {
      if (typeof body.pairingSecret !== "string") return json(400, { error: "invalid_pairing_code" });
      const secretBytes = fromBase64Url(body.pairingSecret);
      if (!secretBytes) return json(400, { error: "invalid_pairing_code" });
      const pairingSecretSha256 = await sha256Hex(secretBytes);
      const { data: session, error } = await admin
        .from("nexus_signaling_sessions")
        .update({
          guest_user_id: user.id,
          join_expires_at: new Date().toISOString(),
          expires_at: new Date(Date.now() + SESSION_WINDOW_MS).toISOString(),
        })
        .eq("id", sessionId)
        .neq("host_user_id", user.id)
        .eq("pairing_secret_sha256", pairingSecretSha256)
        .is("guest_user_id", null)
        .is("closed_at", null)
        .gt("join_expires_at", new Date().toISOString())
        .select("id")
        .maybeSingle();
      if (error) return json(503, { error: "session_service_unavailable" });
      if (!session) return json(404, { error: "pairing_expired_or_invalid" });
      const ice = await iceConfiguration(user.id);
      return json(200, {
        sessionId,
        topic: `nexus-session:${sessionId}`,
        expiresAt: new Date(Date.now() + SESSION_WINDOW_MS).toISOString(),
        iceServers: ice.iceServers,
        relayAvailable: ice.relayAvailable,
      });
    }

    const { data: membership, error: membershipError } = await admin
      .from("nexus_signaling_sessions")
      .select("host_user_id, guest_user_id, expires_at, closed_at")
      .eq("id", sessionId)
      .maybeSingle();
    if (membershipError) return json(503, { error: "session_service_unavailable" });
    if (!membership) {
      return json(404, { error: "session_expired" });
    }
    if (membership.host_user_id !== user.id && membership.guest_user_id !== user.id) {
      return json(403, { error: "session_access_denied" });
    }

    if (body.action === "close") {
      if (membership.closed_at) return json(200, { closed: true });
      const { error } = await admin.from("nexus_signaling_sessions")
        .update({ closed_at: new Date().toISOString() })
        .eq("id", sessionId);
      if (error) return json(503, { error: "session_service_unavailable" });
      return json(200, { closed: true });
    }

    if (membership.closed_at || Date.parse(membership.expires_at) <= Date.now()) {
      return json(404, { error: "session_expired" });
    }

    const ice = await iceConfiguration(user.id);
    return json(200, {
      sessionId,
      topic: `nexus-session:${sessionId}`,
      expiresAt: membership.expires_at,
      iceServers: ice.iceServers,
      relayAvailable: ice.relayAvailable,
    });
  } catch {
    // Never log bearer tokens, pairing secrets, SDP, ICE candidates, or frame data.
    return json(503, { error: "session_service_unavailable" });
  }
});
