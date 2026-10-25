# BrainExporter

**Live EEG. Minimal friction.**

An open-source Android workstation for **RT-BCI / ADS1299** acquisition, real-time EEG monitoring, signal processing and local CSV export.

[**Get the latest APK**](https://github.com/vexpaer/BrainExporter/releases/latest) · [Source code](https://github.com/vexpaer/BrainExporter) · [Processing modules](docs/MODULES.md)

## At a glance

| Acquisition | Analysis | Extensibility | Platform |
| --- | --- | --- | --- |
| 8 EEG channels · BLE · ~250 Hz | Time series · PSD · FFT · EEG bands | Importable validated processing modules | Native Kotlin · Jetpack Compose · Material 3 |

## Designed around the signal

BrainExporter transforms an Android phone into a portable EEG console.

- **Connect.** Discover compatible RT-BCI BLE devices, including FFF0/FFF3/FFF4 and FFE0/FFE1/FFE2 UART profiles.
- **Observe.** Inspect eight EEG channels with live voltage waveforms, spectral views, band-power analysis and impedance estimates.
- **Record.** Start and stop acquisition, saving timestamped EEG samples to CSV locally.
- **Extend.** Enable built-in signal processing or import a validated `.be-module.json` manifest. EEG-to-EEG and EEG-to-features modules share stable interfaces.
- **Stay local.** EEG samples are not sent to a remote server.

## Interface

A deliberately minimal visual system: slowly drifting aurora lighting, quiet instrument panels and data-first monitoring. Navigation, selections and status changes use responsive, spring-like motion. Android's reduced-motion setting stops decorative loops.

The app has four primary sections: **Home, Monitor, Modules and Account**. The EEG acquisition and export workflow remains the focus.

## Quick start

1. Install the [latest APK](https://github.com/vexpaer/BrainExporter/releases/latest) and enable Bluetooth.
2. Open **Monitor**, scan and select your RT-BCI device.
3. Choose **Start recording** to capture EEG. Android 8–9 will request storage permission when necessary.
4. Stop recording to finalize the CSV in `Documents/eegData`.

Files are named like `BrainExporter_EEG_20260812_183000_000.csv`. Columns include `sample_index`, `packet_id`, `received_at_nanos` and eight channel values in µV.

## Architecture

| Module | Responsibility |
| --- | --- |
| `sdk-core` | Stable device, algorithm and processing interfaces |
| `platform-ble-android` | Android BLE UART transport |
| `plugin-device-rtbci` | RT-BCI parsing, scaling, commands and impedance |
| `plugin-algorithm-basic` | FFT, PSD, band power and validated processing engines |
| `core-runtime` | Sessions, data flow, module state and snapshots |
| `plugin-ui-monitor` | Compose UI, visualizations and navigation |
| `app` | Application assembly, permissions and local CSV storage |

## Development

Requires **JDK 17+** and **Android SDK 36**.

```bash
./gradlew --no-daemon test lint assembleDebug
```

CI runs tests, lint and assembly on pull requests. Debug APKs appear in `app/build/outputs/apk/debug/`.

## Safety and privacy

**Research prototype — not a medical device.** Spectral features and impedance estimates are not diagnostic measurements.

ADS1299 impedance testing injects approximately **6 nA at 31.2 Hz**. When electrodes are attached to a person, power the acquisition board from a battery or compliant isolated supply. Never connect worn electrodes to non-isolated USB on a mains-powered computer. Validate BLE packet loss, sampling time and calibration before a study.

EEG recordings stay in `Documents/eegData`. Local account passwords are salted PBKDF2-SHA256 hashes on the device. GitHub release checks support in-app updates.

## License

[MIT](LICENSE). Vendor firmware and third-party acquisition samples are not included.
