# VXV Dual-Engine Switching Architecture

Status: implementation contract for `feature/vxv-isolated-switch`. The Psiphon engine is not yet device-verified.

## Confirmed product contract

The design combines independent VPN environments with persistent, compatible shared helpers:

1. In Xray mode, Xray owns the active Android VPN interface and runs the components required by the selected main profile.
2. In VXV mode, Psiphon owns the active Android VPN interface and runs the components required by VXV.
3. Shared, engine-agnostic capabilities (UI, profile/preferences storage, logging, connectivity observation) remain available when safe.
4. Helpers that retain tunnel-specific sockets, routes, descriptors, or native state are stopped unless the selected engine explicitly consumes them.
5. The coordinator serializes switches, confirms stop before starting the other engine, and restores the previous main profile only if it was running before VXV was opened.

This is independent environments plus compatible shared capabilities and exactly one active system-TUN owner. It does not mean both full-device VPN tunnels route traffic concurrently.

## Main to VXV

1. Snapshot the selected main profile ID and whether the main tunnel was running.
2. Request main-service stop and wait for authoritative service state to confirm it stopped.
3. Start Psiphon only after the main VPN interface is released. If stop times out, abort the transition.
4. Never label VXV connected until the Psiphon engine reports a real connected state.

## VXV to Main

1. Stop Psiphon and await confirmation that its VPN interface and native resources are released.
2. Return to the main screen.
3. Restore the exact previous profile only if the main tunnel was running before VXV.
4. If Psiphon stop cannot be confirmed, do not start Xray over it; surface a recoverable error.

## Engine and helper ownership

- Engine-exclusive: Xray core, Psiphon tunnel/session, and engine-specific adapters.
- Tunnel owner: exactly one service owns the Android VPN interface at a time.
- Shared helpers: UI, preferences, profile storage, logging, connectivity observation.
- Conditional helpers: proxy listeners, tun2socks, Aether, process finders, and native workers run only when the active engine consumes them.
- No fake state: selected profile is not evidence of a live tunnel; a running native process is not evidence that device traffic is routed.

## Failure and lifecycle requirements

- Stop timeout: abort transition and do not start the other full-VPN engine.
- Psiphon startup failure: report failure and clean up any partially created resources.
- Rapid repeated requests: allow only one switch operation at a time.
- Process death or VPN permission revocation: reconstruct state from service-owned truth and release resources.
- Restore only the captured profile and only if the main tunnel was previously active.

## Required validation

1. Main stopped -> VXV -> return leaves main stopped.
2. Main running -> VXV starts only after confirmed main stop.
3. VXV running -> main starts only after confirmed Psiphon stop.
4. Main stop timeout prevents VXV startup.
5. Psiphon start failure never shows connected and leaves no orphaned service.
6. Rapid switch taps produce one transition and one tunnel owner.
7. Process death and VPN permission revocation recover safely.
8. Build supported variants/ABIs and test on a real Android device.

## Current implementation boundary

The branch currently has the VXV entry point and guarded main-service handover, plus the Psiphon tunnel-core AAR dependency declaration. The screen still does not create a Psiphon session: a library dependency by itself is not a running VPN. Before claiming completion, the app needs a compatible Psiphon host/service integration, valid Psiphon configuration and embedded server entries, correct foreground-service/notification lifecycle, stop confirmation, and device-level traffic tests.
