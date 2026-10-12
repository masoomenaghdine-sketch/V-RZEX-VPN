# VØRZEX VPN — API outage and access policy

## Required behavior

The API is the source of truth for remote access restrictions. The client must not interpret an API timeout as proof that a user is banned, and must not silently treat a timeout as an approval.

### New VPN sessions
- When mandatory registration or access-status verification has never succeeded, do not start a new VPN session.
- Show a clear, localized service-unavailable message with Retry. Do not label the user as banned unless the API returned an authenticated denial.
- Retry with bounded exponential backoff (1, 2, 4, 8, 16 seconds; cap at 30 seconds) and jitter. Avoid a tight polling loop.
- A health endpoint alone is not sufficient authorization; require a valid installation token and an explicit allowed access-status response.

### Existing VPN sessions
- A transient API outage must not trigger an unverified remote stop command.
- Cache the last signed/authenticated access decision with its server timestamp and a short maximum age. When that decision expires, the client must stop or suspend according to the product's documented fail-closed policy.
- Never accept remote commands from an unauthenticated cache or replay a command after its expiry.
- After connectivity returns, fetch current access status and pending commands before allowing reconnection.

### Privacy and reliability
- Send only the disclosed device fields required for service management. Do not send VPN profiles, subscription URLs, private keys, browsing history, or traffic contents.
- Device reports must follow the app's disclosed registration/telemetry policy. Do not infer that a network error is user refusal.
- Admin actions should be idempotent and auditable. Report command delivery separately from command execution; the client acknowledges only after execution.
- The Telegram bot should show API/Worker health, last successful check, pending command count, and deployment/database errors without exposing tokens.

## Rollout checklist
1. Wire the configured HTTPS API endpoint and authenticated registration/access-status flow into the Android app.
2. Persist the last valid access decision securely and enforce its expiry.
3. Add bounded retry/backoff and a user-visible retry state.
4. Add client acknowledgements for stop/restriction/cache commands and Android notifications for confirmed restrictions.
5. Test API offline, slow responses, invalid token, revoked access, app process death, and recovery.
6. Build and test a debug APK before claiming the feature is live.

This policy is a design/implementation checklist. It does not by itself activate a gate or change VPN behavior until the Android client and backend enforce it.
