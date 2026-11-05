# BrainExporter — Contributor & Agent Guide

## Product
BrainExporter is a native Kotlin / Jetpack Compose Android workstation for RT-BCI / ADS1299 EEG acquisition. Preserve module boundaries, device protocols, processing logic and the local CSV format.

## Language and visual direction
- All user-facing UI copy and error messages must be **English**.
- Primary navigation: **Home · Monitor · Modules · Settings**. Do not reintroduce the audio tab or home-screen playback controls.
- Use the dark Material 3 system described in DESIGN.md: semantic colors, standard typography, tonal surfaces and native components. The supplied black logo is the brand mark.
- Use a static backdrop under live charts. Keep visual changes inside the Compose UI; do not install React packages.
- Respect Android reduced-motion settings. Decorative loops stop when motion is reduced or the screen is not visible.
- Maintain accessible semantics, minimum 48dp touch targets and readable measurement units.

## Engineering constraints
- Never add cloud EEG upload, analytics, hidden network calls or runtime downloads of decorative assets.
- Impedance measurement must remain disabled during EEG recording.
- Do not change packet parsing, timestamps, file semantics, permission flows or the validated module contract for visual updates.

## Verification
Run `./gradlew --no-daemon test lint assembleDebug`. Inspect GitHub Actions and fix failures. Prefer one final commit per redesign PR. Release tags and signing secrets are out of scope.
