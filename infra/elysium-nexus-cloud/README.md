# Elysium Nexus global connection service

This is an isolated Supabase project for Nexus rendezvous. It is separate
from the MEET Supabase project and must be operated with the project ref
`trccikkcmdqnutwfjrbf`.

## Privacy contract

- Mac and Android create a short lived, one-to-one signaling session.
- The Mac generates a 256-bit pairing secret and an idempotency UUID. The
  database keeps only the secret's SHA-256 digest; the secret remains in Mac
  memory and is passed to Android by QR or an equivalent secure pairing UI.
- Realtime signaling uses a private channel. RLS authorizes only the two
  temporary authenticated peers. Session rows expose no client read access.
- Signaling may contain SDP and ICE candidates, which can reveal connection
  addresses. Encrypt every payload with AES-256-GCM and authenticate the
  envelope with HMAC-SHA-256 before publishing it. Derive independent keys
  from the 32-byte pairing secret using HKDF-SHA-256, the session UUID bytes
  as salt, and the context labels `NEXUS-SIGNAL-ENC-v1` / `NEXUS-SIGNAL-MAC-v1`.
  Prefix the encrypted payload with its random 12-byte GCM nonce; the GCM tag
  is appended by AES-GCM. Use the header as GCM additional authenticated data.
- The header is ASCII
  `NEXUS-SIGNAL-v1\n<session UUID>\n<sender role>\n<sequence>\n<sent-at-ms>\n<kind>`.
  The HMAC input appends `\n<body-base64url>` to that header, where the body
  is nonce+ciphertext+tag encoded as unpadded base64url. Verify the MAC,
  session, sender, timestamp (no more than 30 seconds in the future or 120
  seconds old), and a bounded replay window before decrypting or applying the
  payload. This contract is independent of Swift/Kotlin JSON key ordering.
- The signaling provider still sees that two clients are connected, message
  sizes and timing, and network source addresses. It cannot read SDP/ICE
  payloads or media keys. Both global clients require TURN and enforce
  relay-only mode, refusing direct connections when TURN credentials are
  missing. This hides each endpoint's IP from its paired device; the TURN
  operator still sees source IPs, traffic sizes, and timing.
- Screen, audio, and input data must use WebRTC DTLS-SRTP/DataChannel. A TURN
  server can relay encrypted packets but does not receive media keys. Do not
  send frame data through Supabase Realtime or the session function.
- The Mac must make outbound HTTPS/WSS connections only. Do not expose the
  Mac's TCP port 7878 to the public Internet.
- Closing a session revokes Realtime access immediately. Expired metadata is
  pruned when a new session is created; the database contains no screen frames.

## Required deployment configuration

The Android and macOS source now contains the local global-pairing flow:
short-lived code join, authenticated private signaling, encrypted SDP/ICE,
bidirectional WebRTC video, DTLS-protected trackpad/keyboard controls, and
explicit screen-capture consent on both devices. This source is not yet live
for users because the hosted service and public client configuration have not
been provisioned.

The cloud project must be configured before this path can be called global:

1. Apply this project's migration and deploy the `nexus-session` function.
   Enable anonymous sign-ins and private Realtime channels in the hosted
   project. The clients persist their temporary identities using Android
   Keystore and macOS Keychain.
2. Deploy a coturn-compatible TURN service with UDP and TCP/TLS support
   (preferably `turns:` on port 443) and the REST shared-secret
   mechanism. Set `NEXUS_TURN_URLS` and `NEXUS_TURN_SHARED_SECRET` only as
   Supabase function secrets. Never bundle the TURN secret in either app.
4. Build both clients with the project's public Supabase URL and
   publishable client key. These are client configuration values; service
   role and TURN secrets remain server-side.
5. Verify TURN-relayed sessions between genuinely different
   networks before describing the feature as globally available.

Without TURN, the global clients refuse to connect. `relayAvailable: false`
is an explicit incomplete deployment state; clients must not display “global
connection ready” in that state.

## Session function

`POST /functions/v1/nexus-session` requires a Supabase anonymous-user access
token. `create` accepts a client generated UUID `requestId` and 32-byte
base64url `pairingSecret`; retrying that pair is idempotent and conflicting
secret reuse is rejected. The other `action` values are `join`,
`refresh-turn`, and `close`. Responses disable HTTP caching. Errors do not
echo credentials or input.

## Local checks

Run Supabase CLI commands from this directory so they use this project's
config rather than the MEET project's root `supabase/config.toml`:

```sh
supabase --workdir infra/elysium-nexus-cloud/supabase start
supabase --workdir infra/elysium-nexus-cloud/supabase db reset
supabase --workdir infra/elysium-nexus-cloud/supabase functions serve nexus-session
```

Do not run `supabase db push` or `supabase functions deploy` until the local
migration and client integration have been reviewed and the TURN endpoint is
ready.
