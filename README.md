# APEXMC Launcher (Android)

An Android launcher project for Minecraft: Java Edition, based on the GPL-3.0-licensed Millida Launcher Mobile codebase.

## Current migration status

This repository is being rebranded and audited for APEXMC. The first foundation pass changes the Android app label, application ID, default dark accent and disables guest/offline login paths. The underlying Minecraft runtime and installation code are being preserved while Millida-specific services and integrations are reviewed.

**This is not yet a release APK.** Microsoft sign-in is intentionally not ready until APEXMC configures its own Microsoft Entra application/client ID. Do not reuse the upstream project's client ID, signing key, updater endpoint, CDN, or service credentials.

## Build

See [BUILDING.md](BUILDING.md) for Android/Gradle build requirements. A compatible Java 17 and Android SDK/NDK environment is required. A GitHub Actions debug-APK workflow is available at `.github/workflows/apexmc-android.yml`. Current progress and blockers: [APEXMC-MIGRATION-STATUS.md](APEXMC-MIGRATION-STATUS.md).

## Authentication

The intended production flow is official Microsoft device-code sign-in followed by Xbox Live, XSTS and Minecraft Services profile validation. Players must own Minecraft Java Edition. Guest/offline account bypass is disabled in APEXMC builds.

Before enabling sign-in, register an APEXMC-owned public client application in Microsoft Entra and configure the resulting client ID in the auth implementation. Never commit client secrets, signing keys or passwords.

## License and attribution

This fork remains under **GNU GPL v3.0**. Keep the license and required notices when distributing modified binaries or source. APEXMC is an independent project and is not affiliated with Mojang Studios, Microsoft, or the original Millida project. Minecraft is a trademark of Mojang Studios.


<!-- CI trigger: APEXMC debug build workflow -->

<!-- Trigger CI after Gradle BuildConfig correction -->
