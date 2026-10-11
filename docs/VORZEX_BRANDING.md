# VØRZEX VPN branding assets

The Android app display name is **VØRZEX VPN** (the existing `@string/app_name` remains unchanged).

## Assets added
- `V2rayNG/app/src/main/res/drawable/vorzex_icon_foreground.xml`: scalable neon V / globe / orbit emblem.
- `V2rayNG/app/src/main/res/values/vorzex_brand.xml`: dark navy and neon brand palette.
- `V2rayNG/app/src/main/res/drawable/vorzex_splash_background.xml`: centered brand mark on a dark splash background.

## Integration note
The adaptive launcher icon and activity splash theme must be wired in a follow-up change after validating the current manifest, theme lifecycle, and API-level behavior. Do not replace the existing Tor/Aether integration while applying branding changes. The generated reference PNG should be kept as the visual source of truth for a later bitmap export into all launcher density buckets.
