# VXV Dual-Engine Switching Architecture

Status: design contract for the isolated `feature/vxv-isolated-switch` branch. This document does not claim that the Psiphon engine is integrated or device-tested.

## Product behavior

VXV is a separate in-app VPN environment powered by Psiphon. The main environment remains powered by the existing Xray/v2fly/Aether stack. A central switch coordinator owns transitions between them and remembers whether the main tunnel was active before entering VXV.

### Main → VXV

1. Snapshot the selected main profile ID and whether the main tunnel was genuinely running.
2. Request a stop of the main VPN service and wait for authoritative service state to confirm the VPN interface is released.
3. Start the VXV Psiphon session only after that confirmation. If stop times out, do not start VXV; show an error and remain in the main environment.
4. Keep only helper processes that have an explicit, verified compatibility contract with the active Psiphon mode. Do not keep the Xray core running merely to make it appear alive.

### VXV → Main

1. Stop Psiphon and await confirmation that it has released its VPN interface and native resources.
2. Return to the main screen.
3. Restore the exact main profile only if the main VPN was running before the transition. If it was already stopped, leave it stopped.
4. Never report connected until the active engine reports a real connected state.

## Tunnel ownership constraint

Android's `VpnService.Builder.establish()` creates the app's VPN interface. The existing `CoreVpnService` owns a TUN descriptor and feeds it to the existing core/tun2socks pipeline. The upstream Psiphon Android app's full-VPN mode also owns a VPN interface. Starting both full-VPN implementations concurrently is not a valid design.

The preferred coexistence design is one active system-TUN owner at a time. A Psiphon proxy-only mode may be used as an upstream for a compatible tunnel adapter only after the exact SDK artifact's API, SOCKS listener, socket protection, DNS handling, IPv4/IPv6 routing, and failure semantics are verified. Until then, proxy-only mode must not be described as a full-device VPN.

## Capability policy

Each component must declare a capability/ownership contract instead of being toggled by a broad "engine on/off" flag:

- **Engine-exclusive:** Xray core, Psiphon tunnel/session, each engine's tunnel adapter.
- **Tunnel owner:** exactly one component owns the Android VPN interface at a time.
- **Shared helpers:** logging, preferences, profile storage, UI, connectivity observation, and other demonstrably engine-agnostic components may remain alive if they do not retain sockets/routes/resources tied to the previous tunnel.
- **Conditional helpers:** proxy listeners, tun2socks, Aether, process finders, and native workers remain active only when the selected engine explicitly consumes them.
- **No fake state:** a selected profile is not evidence that its engine is running; a running native process is not evidence that traffic is routed through it.

## Failure handling

- Stop timeout: abort transition; do not start the other full-VPN engine.
- Psiphon startup failure: report failure and leave no stale VXV "connected" state. Offer a controlled return to the main screen.
- Main restoration: restore only the captured profile and only if the main tunnel was running before entering VXV.
- Process death/restart: reconstruct state from service-owned truth, not a UI-process Boolean.
- Always clean up native workers, descriptors, listeners, notifications, and callbacks owned by the engine being left.

## Required tests before calling this complete

1. Main stopped → enter VXV → return: main remains stopped.
2. Main running → enter VXV: main tunnel is confirmed stopped before Psiphon starts.
3. Main running → VXV connection succeeds → return: Psiphon is confirmed stopped before the original main profile restarts.
4. Main running → main stop times out: VXV does not start.
5. Psiphon start fails: no connected UI state and no orphaned service/resources.
6. Rapid repeated switch taps: only one transition and one active tunnel owner.
7. Process death / Android VPN permission revocation: resources are released and state recovers safely.
8. Build every supported ABI and verify the exact Psiphon SDK API in the artifact declared by Gradle.

## Current implementation boundary

The current branch includes the VXV entry point and main-service handover guard, plus a Psiphon SDK dependency declaration. The VXV screen is still a placeholder: it does not yet start or manage a real Psiphon session. This document defines the target behavior and prevents the placeholder from being mistaken for a working VPN.
