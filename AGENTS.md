# BrainExporter — Contributor & Agent Guide

## Product
BrainExporter is a native Kotlin / Jetpack Compose Android workstation for RT-BCI / ADS1299 EEG acquisition. Preserve module boundaries, device protocols, processing logic and the local CSV format.

## Language and visual direction
- All user-facing UI copy and error messages must be **English**.
- Primary navigation: **Home · Monitor · Modules · Account**. Do not reintroduce the audio tab or home-screen playback controls.
- Keep the dark-only aurora visual system: four blended drifting lights, subtle grain, and data-first monitoring. Use a quiet/static backdrop under live charts.
- Inspired by Arc UI's motion principles: animate state changes and spatial transitions with springs and responsive easing. Do not install React packages into Compose.
- Respect Android reduced-motion settings. Decorative loops stop when motion is reduced or the screen is not visible.
- Maintain accessible semantics, minimum 48dp touch targets and readable measurement units.

## Engineering constraints
- Never add cloud EEG upload, analytics, hidden network calls or runtime downloads of decorative assets.
- Impedance measurement must remain disabled during EEG recording.
- Do not change packet parsing, timestamps, file semantics, permission flows or the validated module contract for visual updates.

## Verification
Run `./gradlew --no-daemon test lint assembleDebug`. Inspect GitHub Actions and fix failures. Prefer one final commit per redesign PR. Release tags and signing secrets are out of scope.
