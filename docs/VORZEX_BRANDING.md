# VØRZEX VPN branding assets

The app display name is **VØRZEX VPN**. The existing `@string/app_name` remains unchanged.

## Assets
- `V2rayNG/app/src/main/res/drawable/vorzex_icon_foreground.xml`: scalable neon V / globe / orbit vector artwork.
- `V2rayNG/app/src/main/res/values/vorzex_brand.xml`: dark navy and neon brand colors.
- `V2rayNG/app/src/main/res/drawable/vorzex_splash_background.xml`: centered brand mark on a dark splash background.
- `V2rayNG/app/src/main/res/values/vorzex_launch_theme.xml`: launch theme used by MainActivity.
- `V2rayNG/app/src/main/res/mipmap-anydpi/ic_launcher.xml` and `ic_launcher_round.xml`: adaptive launcher icons using the VØRZEX artwork.

## Notes
- The original supplied raster image remains the visual reference; the repository currently uses a scalable vector recreation inspired by it, not the exact PNG.
- Existing density-specific legacy PNG launcher assets have not been replaced, so devices below Android 8 may still display the previous icon until those PNGs are exported and updated.
- The main activity restores the normal app theme immediately during `onCreate` so the splash background does not remain as the app's window background.
- Keep the existing Tor/Aether integration, including the Tor-only profile entry, intact while making branding changes.
