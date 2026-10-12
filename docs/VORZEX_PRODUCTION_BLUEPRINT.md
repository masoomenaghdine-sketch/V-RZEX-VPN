# VØRZEX VPN — Production Architecture Blueprint

Status: **design specification; not an implementation claim**

This document turns the VØRZEX scope into staged, testable requirements. Existing source code, deployment state, and CI results must be verified independently before any feature is marked implemented.

## 1. Core identity model

- **User**: backend-owned identity; never derived from a VPN profile UUID, Aether identity, private key, or server credential.
- **Installation**: one app installation with an independently generated opaque `installationId`. A user may own multiple installations.
- **Installation credential**: a stable installation identity is distinct from the secret used to authenticate requests. “Stable token” must not mean an unexpiring bearer secret. Prefer a stable installation ID plus short-lived access credentials and rotating refresh credentials; revoke credentials server-side when needed.
- **Profile metadata**: only non-secret metadata such as label, source category, protocol family, and ownership class. Never upload raw configuration, private keys, passwords, subscription URLs, location, browsing history, or other unnecessary secrets.

## 2. Backend components

- Cloudflare Worker: versioned HTTPS API, authentication, authorization, input validation, rate limiting, correlation IDs, and error mapping.
- Cloudflare D1: users, installations, credential metadata/hashes, profiles metadata, policies, sessions, events, command queue, admin audit records, feature flags, and migration ledger.
- Telegram bot: a thin administrative client. It calls the Admin API and never accesses D1 directly or embeds privileged database credentials.
- Future web admin panel: consumes the same Admin API; Telegram is not the source of truth.
- Owned-server adapters: optional and explicitly scoped to infrastructure controlled by VØRZEX. Third-party profile revocation cannot be represented as server-side enforcement.

## 3. API contract requirements

All routes are versioned under `/v1`. Contract tests must define request/response schemas, authentication requirements, error codes, and compatibility guarantees before implementation.

Suggested client areas:
- installation registration and credential refresh/re-registration
- access status and policy retrieval
- telemetry batch upload and event acknowledgement
- session lifecycle and heartbeat
- remote command polling/acknowledgement/result reporting
- minimal profile metadata

Suggested admin areas:
- user/installation/session search and details
- user and installation enable/disable/revoke operations
- policy changes with expiry and mandatory reason
- command status and failed-event queue inspection
- aggregate reporting, service health, feature flags, and audit access

Every mutation must be authorized on the server. Hiding a Telegram button is not authorization.

## 4. Token lifecycle

- Expiration, refresh, rotation, replay detection, and revocation semantics must be specified in one shared contract.
- Do not store bearer or refresh tokens in plaintext in D1; store a verifier/hash where practical.
- Refresh credentials rotate atomically; reuse of an invalidated refresh credential revokes or challenges the affected token family according to the agreed policy.
- Use TLS, Android secure storage appropriate to the threat model, bounded lifetimes, and redaction in logs.
- A stable installation ID is not an authentication secret and must never be treated as one.

## 5. Telemetry and event queue

- Allowlist event types and payload fields. Apply payload-size limits, per-installation quotas, and server-side validation.
- Each event has an opaque unique `eventId`, timestamp, installation/session reference, schema version, and correlation/request ID as appropriate.
- Local queue stores only approved, minimal events; retry with exponential backoff and jitter; delete only after an explicit server acknowledgement.
- Backend deduplicates by event ID with a uniqueness constraint and returns per-event accepted/duplicate/rejected results.
- Expose pending, retried, rejected, and dead-letter counts to authorized admins without exposing secrets.
- Telemetry failure must not break the VPN tunnel or block local user control.

## 6. Session model and evidence source

Suggested states: `starting`, `active`, `ended`, `stale`, `unknown`. Define legal transitions and timestamps. Record source as `client_reported`, `server_verified`, or `estimated`; never present a client report as server-confirmed truth.

Heartbeat timeout moves a session to `stale`, not automatically to `ended`. Only an owned-server integration may report server-verified connectivity when it has trustworthy evidence. Third-party profiles can only be controlled locally by the app.

## 7. Access policy

Policies may apply at user, installation, or profile scope. Define precedence explicitly (global/user/installation/profile), expiry, reason, and audit requirements. Access states include enabled/allowed, disabled, revoked, and expired.

Offline behavior must be explicit: use the last valid policy only within a documented cache lifetime and fail according to the chosen risk policy after expiry. A temporary API outage must not silently be interpreted as permission to bypass a revocation policy. Remote disconnect is best-effort for an online client; offline clients can only apply it when they reconnect unless an owned server independently enforces revocation.

## 8. Telegram admin security

- Authenticate the Telegram identity against a server-side allowlist and map it to explicit roles.
- Enforce Owner/Admin/Moderator/Viewer permissions inside the Worker.
- Require confirmation for high-impact changes; bind confirmation to actor, target, action, expiry, and a one-time nonce.
- Require a reason for disable/revoke operations.
- Write append-only application audit events with actor, action, target, timestamp, reason, old/new state, request ID, and result. Restrict direct database mutation paths.
- Rate-limit commands and protect bot secrets as deployment secrets.

## 9. Data governance

- Document purpose and retention for telemetry, heartbeat, sessions, and audit records.
- Minimize IP and device data; avoid collecting location, browsing history, raw profiles, or credentials.
- Provide authenticated export/deletion workflows where applicable, with clear handling of legally/operationally required audit records.
- Define retention jobs and verify deletion/anonymization behavior with tests.

## 10. Reliability and operations

- Health endpoints report component state without leaking secrets.
- Add structured logs, request IDs, latency/error metrics, migration status, and alerts.
- Use bounded retries with exponential backoff and jitter; do not retry non-idempotent mutations without an idempotency key.
- Add circuit breakers or temporary backoff when Telegram, D1, or an owned-server adapter is unhealthy.
- Use versioned D1 migrations and test them against a fresh database and a prior schema.
- Define backup/export and recovery procedures based on the selected Cloudflare plan and available service capabilities.
- Separate development/test/production secrets and environments.

## 11. Compatibility and feature flags

- Keep API versions under `/v1`; define deprecation windows before breaking changes.
- Support a declared client version range and a minimum-version policy.
- Remote configuration is restricted to validated, non-secret settings with safe bounds (e.g. heartbeat interval, recheck interval, minimum supported version).
- Feature flags are server-controlled, audited, and default to safe values when unavailable.

## 12. Release gates

A release is not production-ready until:
1. contract, unit, integration, authorization, replay/idempotency, migration, and failure-mode tests pass;
2. Android debug APK builds and installs on supported test devices;
3. VPN connect/disconnect is verified independently of telemetry;
4. the client talks to a deployed test backend and exercises registration, access status, event acknowledgement, and token expiry/refresh;
5. admin actions are tested against unauthorized and authorized identities;
6. the release APK is signed with a protected release key and its signature/install are verified;
7. GitHub Release asset and direct download URL are checked from the actual uploaded artifact.

## 13. Delivery phases

1. Repository and CI audit; identify the real base branch and release workflow.
2. Freeze client/admin contracts, token lifecycle, session transitions, and offline policy.
3. Implement D1 migrations and Worker API with automated tests.
4. Implement admin authorization and Telegram adapter.
5. Connect Android registration, access status, telemetry queue, heartbeat, and session reporting.
6. Add owned-server enforcement only for servers under project control.
7. Add self-healing/observability and run fault-injection tests.
8. Build, sign, verify, and publish debug then release artifacts.

## 14. Status discipline

Documentation is not implementation. A feature is marked **implemented** only when code exists; **verified** only when a reproducible test or deployment check passes; **released** only when the actual artifact is published and checked.
