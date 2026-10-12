# VØRZEX Deadpool — Self-Healing and Recovery Specification

Status: **design specification; not an implementation claim**

## Goal

Recover automatically from transient failures, preserve user connectivity where safe, and make failures diagnosable. The system must never claim recovery until a health check confirms it.

## Failure classes and response

| Failure | Automated response | Escalation |
|---|---|---|
| Telegram API timeout / 429 | bounded retry, respect `Retry-After`, exponential backoff + jitter | alert if failure budget exceeded |
| Worker transient 5xx | client/bot retry only for safe/idempotent operations | monitor elevated error rate |
| D1 busy/temporary failure | bounded retry for safe reads/idempotent writes; transaction for state changes | alert; preserve command/event for retry |
| Duplicate webhook/update | deduplicate by Telegram update ID and idempotency key | record duplicate metric, no duplicate mutation |
| Poison event / invalid payload | validate, quarantine/dead-letter with reason code | show pending review count to admins |
| Stuck remote command | command lease/expiry; safe retry only with idempotency key | mark unresolved and notify admin |
| Repeated dependency failures | backoff/circuit breaker where appropriate; avoid retry storms | alert with correlation ID |
| Bad remote configuration | schema/range validation; retain last-known-good non-secret config | reject change and notify admin |
| Failed migration | stop rollout; do not blindly reverse destructive changes | operator-guided recovery from backup/export |
| Android telemetry upload failure | persist approved events locally within quota; retry later | expose queue depth when next online |
| VPN engine failure | surface actionable error and preserve logs without secrets | do not let telemetry recovery code restart/alter tunnel blindly |

## Required design properties

1. **Bounded retries:** every retry loop has a maximum attempt count, deadline, and backoff with jitter.
2. **Idempotency:** mutations carry an idempotency key or are designed so duplicate execution cannot repeat the side effect.
3. **Dead-letter queue:** exhausted or invalid events/commands are retained with minimal metadata, reason code, attempt count, and timestamps. They can be inspected and replayed only by an authorized operator after validation.
4. **Health checks:** distinguish liveness (process responds) from readiness (dependencies available). Never expose secrets or personal data in health output.
5. **Recovery verification:** after a retry/restart/reconnect, run a health or state check and record success/failure.
6. **Circuit breaking:** when a dependency repeatedly fails, pause traffic to it temporarily rather than increasing load.
7. **Alert deduplication:** group repeated alerts by failure signature and apply a cooldown; always retain counts and first/last-seen timestamps.
8. **Auditability:** automated remediation records the trigger, attempted action, result, and correlation ID.
9. **Least privilege:** self-healing code may only perform an explicit allowlist of recovery actions. It cannot edit source code, change access policy, rotate production secrets, or deploy new versions automatically.
10. **Safe degradation:** telemetry, Telegram, and reporting failures do not directly stop a functioning VPN tunnel. Access revocation and owned-server enforcement follow the explicit security policy, not a blanket fail-open rule.
11. **Human approval for destructive actions:** database deletion, schema rollback, mass revocation, secret replacement, and production deployment require an authorized human.
12. **No secret leakage:** redact bearer tokens, bot tokens, private keys, raw configs, subscription URLs, and sensitive request bodies from logs and alerts.

## Telegram admin experience

Suggested commands (names are illustrative until the bot contract is frozen):
- `/health` — component health summary
- `/incidents` — active incidents and last recovery attempts
- `/deadletters` — counts and safe metadata for quarantined items
- `/retry <incident-id>` — request a bounded, authorized retry for eligible idempotent work
- `/incident <id>` — timeline, failure class, attempt count, result, and request ID

Commands must use the Admin API. The bot must not connect directly to D1 or execute arbitrary shell commands.

## Cloudflare implementation notes

- Use Workers for request handling and scheduled checks where supported by the deployment plan.
- Use D1 for durable incident summaries, idempotency records, command/event state, and audit metadata.
- Use Cloudflare Queues or another explicitly provisioned queue if durable asynchronous delivery is required; do not assume D1 alone is a managed message queue.
- Use Cron Triggers for periodic stale-session checks and bounded maintenance tasks only after checking the configured schedule and plan limits.
- Respect platform quotas and retry behavior; avoid unbounded recursive self-invocation.
- Define a recovery runbook for Worker deployment rollback, D1 restore/export, bot token replacement, and dependency outages.

## Acceptance tests

- Telegram 429 honors `Retry-After` and does not produce a retry storm.
- A duplicate webhook update performs a mutation at most once.
- A duplicate telemetry event is acknowledged without creating a second record.
- An invalid/oversized event is rejected and does not crash the Worker.
- A transient D1 failure retries only safe operations and eventually produces a clear result.
- Exhausted retries create a dead-letter record and one deduplicated alert.
- Recovery is not marked successful until the subsequent health check passes.
- Admin commands from a non-allowlisted Telegram ID are rejected server-side.
- Self-healing cannot execute arbitrary code or change production policy.
- Telemetry/Telegram outages do not themselves stop the local VPN tunnel.
