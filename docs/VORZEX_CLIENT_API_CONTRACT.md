# VØRZEX VPN — Client API Contract

This contract belongs to the Android app repository. The backend, database, Telegram bot, and admin panel belong to a separate repository and are not provisioned or deployed here.

## Separation of responsibilities

- **This repository:** Android API client, request/response DTOs, and the contract below.
- **Separate backend repository:** public API service, database, Telegram admin bot, panel workflows, policy evaluation, server integrations, and audit log.
- The Telegram bot must call the backend's admin routes. It must never call the Android app directly or connect directly to the app's local MMKV storage.
- Admin-only routes are implemented and authorized by the backend; they are not exposed as privileged client calls in the Android APK.

## Android client methods

The Kotlin client is in `com.v2ray.ang.api.vorzex.VorzexApiClient`. Its base URL is supplied by the caller. There is intentionally no hard-coded production URL and no request is sent merely by constructing the client.

| Method | HTTP route | Auth | Purpose |
| --- | --- | --- | --- |
| `registerInstallation` | `POST /v1/installations/register` | Consent-based initial registration | Register a user-approved installation and receive a server-issued installation token |
| `sendEvent` | `POST /v1/telemetry/events` | Installation bearer token | Submit an allowlisted, minimal client-reported event |
| `getAccessStatus` | `GET /v1/client/access-status` | Installation bearer token | Fetch the managed service's access policy |

### Registration request

```json
{
  "installationId": "client-generated-stable-installation-id",
  "appVersion": "2.3.10",
  "consentedAt": "2026-10-11T12:00:00Z"
}
```

The backend must validate and bind the installation to a server-issued credential. An installation ID supplied by the client is a lookup hint, not proof of identity. Never silently reuse profile GUIDs, subscription IDs, or Aether identity keys as account IDs.

### Event request

```json
{
  "eventId": "unique-event-id",
  "installationId": "installation-id",
  "eventType": "vpn_connected",
  "occurredAt": "2026-10-11T12:01:00Z",
  "appVersion": "2.3.10",
  "profileLabel": "Optional user-approved display label",
  "profileSource": "owned"
}
```

Allowed event values: `app_started`, `vpn_connecting`, `vpn_connected`, `vpn_disconnected`, `heartbeat`.

These events are client-reported and may be missing, delayed, duplicated, or spoofed. They must not be represented as authoritative server-side connection proof. Use idempotency on `eventId` and rate limits on the backend.

### Access response

```json
{
  "allowed": true,
  "reasonCode": null,
  "message": null,
  "recheckAfterSeconds": 300
}
```

The client can enforce a managed-service policy only where the app or owned server integration controls the relevant connection path. A response from this API cannot revoke a third-party account or guarantee that an imported third-party profile is blocked outside this app.

## Backend-only administrative capabilities

The separate backend should expose authenticated admin routes to its Telegram bot and any admin UI:

- Search users by existing ID without changing it.
- Inspect installation metadata, profile labels, and recent client-reported sessions.
- Read or change user/installation access policy with a required reason and confirmation.
- View aggregate reports and a paginated, append-only admin audit log.
- Integrate owned server control planes where supported.

Suggested backend-only routes:

- `GET /v1/admin/users?query=...`
- `GET /v1/admin/users/{id}`
- `GET /v1/admin/sessions?userId=...&from=...&to=...`
- `GET /v1/admin/access/{id}`
- `PUT /v1/admin/access/{id}`
- `GET /v1/admin/audit-log`
- `GET /v1/admin/report`

Every admin route must independently verify administrator authorization on the backend, validate inputs, rate-limit requests, and audit access-changing actions. A Telegram numeric-ID allowlist is useful but must not be the only security boundary if the bot token or backend is compromised.

## Privacy and security requirements

- Do not send telemetry unless the user has explicitly opted in and can later opt out.
- Do not transmit raw profile configs, private keys, passwords, full subscription URLs, or Aether identity material.
- Keep installation tokens in platform-protected private storage; never embed a shared secret or bot token in the APK.
- Require HTTPS. Do not follow redirects for API requests.
- Use minimal fields, bounded event frequency, server-side validation, idempotency, retention limits, and auditable admin actions.
- The Android client must fail safely when the API is unreachable. It must not disable the user's local VPN profile merely because the admin API is offline.
- The client DTOs and routes are a contract scaffold; they are not a deployed service. The separate backend must implement the same schema before these calls can succeed.

## Integration status

The client DTOs and HTTP client are added as an isolated package. They are not yet wired to UI consent controls, persistent installation-token storage, VPN lifecycle events, or connection enforcement. No requests are sent automatically. The backend URL and server implementation must be supplied by the separate project before end-to-end testing.
