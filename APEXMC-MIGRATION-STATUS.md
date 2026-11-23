# APEXMC migration status

Branch: `apexmc/rebrand-foundation`

This is an in-progress fork of the GPL-3.0 Millida Android launcher. It is not a finished APEXMC release.

## Implemented in this branch

- Android app label changed to **APEXMC**.
- Android application ID changed to `com.apexmc.launcher` (debug package: `com.apexmc.launcher.debug`).
- Default UI accent changed to crimson on a near-black background; adaptive launcher glyph changed to an APEX-style mark.
- App theme resource names and application class reference changed to APEXMC.
- Guest/offline account creation disabled in normal and debug builds.
- Millida self-updater and third-party store links disabled.
- Microsoft device-code auth now reads an APEXMC-owned public client ID from the Gradle property `apexmc.microsoftClientId` or environment variable `APEXMC_MICROSOFT_CLIENT_ID`. The placeholder is intentionally nonfunctional until an APEXMC Entra app is registered.
- Added a GitHub Actions debug-APK workflow.

## Not yet implemented / not verified

- Microsoft sign-in is not ready until a real APEXMC Entra client ID is configured and tested. The upstream client ID is not reused.
- Millida account, portal, hosting, friends/chat, shop, update, and other service integrations still exist in the inherited source and must be removed or replaced before calling this a full rebrand.
- Some screens still contain Millida names and URLs.
- Application source package names still use `net.millida.launcher`; only the Android application ID has changed so far.
- Runtime packaging, installation, launch, graphics and input must be built and tested on a real Android device.
- No release signing key, production updater, privacy policy, support endpoint, or public APK release has been configured.

## Build workflow

The workflow `.github/workflows/apexmc-android.yml` attempts a debug APK build. A green workflow run and a successful install/game-launch test are required before treating the APK as usable. Debug APKs are test artifacts, not signed production releases.

## Licensing

The fork retains the upstream **GNU GPL v3.0** license and required notices. Any distributed modified source/binary must comply with GPL-3.0. APEXMC is not affiliated with Microsoft or Mojang Studios.
