# VØRZEX VPN — Admin API Design (Repository-Only Plan)

## Scope and non-goals

This document defines an implementation-ready plan for administrative telemetry and access management. It does **not** provision Cloudflare resources, create a Telegram bot, deploy a Worker, create a D1 database, add secrets, or send user/device data anywhere. No runtime tracking is enabled by this document.

## Initial code review

Reviewed the current project branch `feature/vxv-tor-slipnet-replacement`, including `MainActivity.kt`, `MainViewModel.kt`, `MainRepository.kt`, `SettingsManager.kt`, `MmkvManager.kt`, `AppConfig.kt`, and `AetherIdentityManager.kt`.

- Profile records and settings are managed locally through MMKV-backed storage. Profile GUIDs, subscription IDs, and Aether cryptographic/device identities are different concepts and must not be treated as a global account ID.
- `AetherIdentityManager` stores Aether protocol identities and key material under the app's private files directory. These are **not** suitable as the account ID for an admin dashboard.
- This first pass did not establish that the app already has a remote, server-side user registry or a stable account ID. Before implementing any upload, trace every candidate ID generator and every write/read path; preserve existing IDs.
- The app has local connection lifecycle events in the main/service flow that could support session events, but a client-reported event is not authoritative proof that a remote server connection is still active.
- No Cloudflare/Telegram provisioning or deployment is included.

## Proposed architecture

1. **Android client** — opt-in, visible telemetry setting; reports only a minimal event payload over HTTPS. Keep local profile IDs separate from the persistent app installation ID and the backend user record.
2. **Cloudflare Worker (future deployment)** — API gateway, request authentication, input validation, rate limits, admin authorization, and command handling.
3. **Cloudflare D1 (future deployment)** — user/install registry, consent state, session events, access policy, and audit log.
4. **Telegram Bot API (future deployment)** — admin interface only. The bot calls the Worker; it does not connect directly to D1 and never contains a database credential.
5. **Owned server enforcement (optional, for self-operated endpoints)** — authoritative access revocation and active-session termination where the server protocol supports it. Third-party endpoints cannot be forcibly managed without their owners' cooperation.

## Suggested data model

- `users`: internal UUID, existing app ID if one is found, status, created_at, updated_at.
- `installations`: internal UUID, user UUID, app version, Android version, device model (optional), consent timestamp, last_seen_at, revoked_at.
- `profiles`: internal UUID, installation/user reference where appropriate, source category (`owned`, `third_party`, `subscription`, `local_import`), protocol type, non-secret display label, optional endpoint fingerprint.
- `sessions`: internal UUID, user UUID, installation UUID, profile reference if known, started_at, last_heartbeat_at, ended_at, end_reason, event_source.
- `access_policies`: user/installation scope, enabled flag, reason code, changed_by_admin, updated_at.
- `admin_audit_log`: Telegram admin numeric ID, action, target internal ID, timestamp, outcome, request/correlation ID. Avoid storing bot tokens or full configs.

Do not store private keys, passwords, UUID credentials embedded in configs, full subscription URLs, raw config blobs, or Aether identity key files in D1 or Telegram messages.

## API contract to implement later

All endpoints must be HTTPS-only, authenticated, versioned under `/v1`, validated, and rate-limited.

- `POST /v1/installations/register` — register an installation after consent; return a server-issued installation credential.
- `POST /v1/telemetry/events` — accept a small allowlisted set of events such as `app_started`, `vpn_connecting`, `vpn_connected`, `vpn_disconnected`, and `heartbeat`.
- `GET /v1/client/access-status` — return whether managed service access is allowed and when to recheck.
- `GET /v1/admin/users?query=...` — admin-only search by existing ID or internal ID.
- `GET /v1/admin/users/{id}` — show permitted device/app metadata, last activity, session estimates, and profile labels.
- `POST /v1/admin/users/{id}/access` — enable/disable access policy with a required reason and audit entry.
- `GET /v1/admin/sessions` — list recent sessions with explicit time range and pagination.
- `GET /v1/admin/audit-log` — admin-only, paginated audit history.

Never use an untrusted client-supplied user ID as proof of identity. Bind the installation to a server-issued credential and rotate/revoke credentials when needed. Protect administrative routes with an allowlist of Telegram numeric user IDs plus a separate Worker-side authorization check.

## Telegram command concepts

- `/find <existing-id>` — search a user without modifying their current ID.
- `/user <internal-id>` — show minimal user/install details.
- `/sessions <internal-id>` — show recent reported sessions and whether their duration is estimated.
- `/access <internal-id>` — show current policy.
- `/disable <internal-id> <reason>` and `/enable <internal-id> <reason>` — request policy changes after confirmation.
- `/report` — aggregate counts and service health, not raw private configs.
- `/audit` — show recent administrator actions.

Require confirmation for destructive or access-changing actions. Log who did what and when. Keep the administrator allowlist outside the repository in deployment secrets/configuration.

## Mixed configuration sources: realistic limits

- **Owned servers:** can provide stronger enforcement if their server-side authorization system is integrated. The exact design depends on the protocols and server control plane.
- **Third-party or imported configs:** the app can report a local profile label/type and local connection state if the user consents. It generally cannot verify remote server-side session state, obtain authoritative usage totals, or revoke the third-party account.
- **Subscriptions:** a subscription URL may be a bearer secret. Never upload or display it in Telegram. Prefer a one-way fingerprint and a user-approved label if correlation is needed.

Do not promise a global “disconnect all” or reliable usage accounting across third-party endpoints. For client-only profiles, a block policy can stop the official app from starting a new managed connection only after the app checks policy; it cannot prevent a modified client or another VPN app from using the same third-party configuration.

## Privacy, security, and reliability requirements

- Add a clear privacy notice and explicit opt-in before any device telemetry is transmitted; provide an off switch and data deletion/contact process.
- Collect the minimum fields. Device model should be optional. Avoid precise location, browsing history, destination domains, packet contents, and unrelated installed-app lists.
- Use short retention for heartbeat/session events and documented retention for audit logs.
- Authenticate installations independently of the existing user ID; rate-limit requests; validate payload sizes; protect against replay; keep secrets server-side; never put the Telegram bot token in the Android app.
- Treat heartbeat-based durations as estimates. Mark stale sessions as `unknown/stale`, not definitively connected.
- Fail closed only for access to the service that this project controls, and provide a clear user-facing explanation if policy checks fail; do not silently disrupt unrelated device networking.
- Provide a test mode and test accounts before enabling real users.

## Implementation order

1. Finish a targeted source audit to locate any existing user/device ID generation and storage. Record exact source locations and preserve IDs.
2. Add a privacy/consent UX and a local telemetry setting; do not transmit anything until a backend endpoint is configured and the user opts in.
3. Implement the Worker/D1 source as repository code only, with migrations, tests, and an example environment file containing placeholders only. Do not deploy it or create Cloudflare resources as part of repository changes.
4. Implement Android event reporting with retry limits, event deduplication, minimal payloads, and no blocking of VPN startup if telemetry is unavailable.
5. Add the Telegram bot backend with strict admin allowlisting, confirmations, and audit logging.
6. Integrate enforcement for owned servers only, then test third-party profiles separately.
7. Run unit tests, static checks, and a privacy/security review before release.

## Acceptance criteria

- Existing user/profile IDs are unchanged.
- No telemetry is sent before explicit opt-in.
- No secrets or full configurations are stored in logs, Telegram, or database records.
- Non-admin Telegram users cannot read or change user records.
- Access changes are auditable and clearly distinguish policy state from actual tunnel state.
- Third-party configurations are labeled as unmanaged unless a supported owner integration exists.
- Repository code and tests can be reviewed without deploying external services.
