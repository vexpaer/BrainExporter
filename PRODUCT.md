# BrainExporter

A native Android research workspace for RT-BCI / ADS1299 EEG acquisition.

## Users and workflow

Researchers connect a compatible BLE board, inspect eight channels, record timestamped CSV files and analyze EEG through waveform, PSD, FFT and band-power views. ADS1299 impedance estimation is available outside recording sessions.

## Interface

English UI, dark Material 3 theme, supplied black brain/export logo. Primary navigation: Home · Monitor · Modules · Settings. See DESIGN.md for the current visual contract. Earlier planetary and aurora design briefs are historical.

## Capabilities

- RT-BCI BLE profiles FFF0/FFF3/FFF4 and FFE0/FFE1/FFE2.
- Eight channels at approximately 250 Hz, with link metrics.
- Local CSV in Documents/eegData.
- Built-in and validated declarative processing modules; no external DEX/JAR execution.
- Optional device-local accounts and GitHub release updates in Settings.

## Constraints

Preserve packet parsing, timestamps, file format, module contracts and permission flows. No EEG cloud uploads or analytics. Impedance tests remain disabled during recording. Research prototype, not a medical device.

## Accessibility

Readable units, semantic controls, minimum 48dp touch targets, scalable text, safe insets and reduced-motion support. Live chart performance takes priority over decoration.
