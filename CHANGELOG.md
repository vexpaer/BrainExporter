# Changelog

## 0.2.0 — 2026-08-13

- Rebuild the focus/rest scene with horizontally rotating 3D spherical projection, continuous surface texture and a correctly occluded continuous orbit.
- Restore the gray/green EEG connection indicator, add mode descriptions and a two-dot swipe affordance.
- Check GitHub Latest Release automatically and support direct APK download plus Android installer handoff.
- Add a module navigation page with validated, persistent `.be-module.json` imports.
- Add stable EEG-to-EEG and EEG-to-features processing contracts with automatic visualizations.
- Ship a continuous Butterworth 1–40 Hz band-pass module and a multi-feature window-statistics reference module.
- Add module package examples, interface documentation and processing tests.

## 0.1.0 — 2026-08-12

- Rename the application and package namespace to BrainExporter.
- Add swipeable focus and rest planets with playback-driven rotation.
- Add configurable HTTPS streaming audio without bundled media files.
- Add explicit EEG acquisition controls and CSV export to `Documents/eegData`.
- Add device-local registration and login with salted PBKDF2 password hashes.
- Link Help & About to the public GitHub repository.
- Preserve the existing BLE monitor, EEG analysis, and impedance workflows.
