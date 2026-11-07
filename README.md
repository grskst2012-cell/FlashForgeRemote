# Printer Remote

![Platform](https://img.shields.io/badge/platform-Android%208%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![Build](https://github.com/grskst2012-cell/FlashForgeRemote/actions/workflows/build.yml/badge.svg)
![License](https://img.shields.io/badge/license-MIT-green)

[English](README.md) · [Русский](README.ru.md)

An Android remote for 3D printers over the local network: no cloud, no accounts. It started as a FlashForge Adventurer 4 remote and now speaks four protocols (FlashForge, Klipper/Moonraker, OctoPrint).
It ships in **two looks built from one codebase**, which install side by side.

> A personal, experimental project. Only the FlashForge Adventurer 4 has been verified on real hardware.

| Flavor | applicationId | Look |
|---|---|---|
| `nothing` | `com.grsks.printerremote.nothing` | Monochrome dot-matrix display, Space Mono, a single accent (red / mono / amber) |
| `material` | `com.grsks.printerremote.m3` | Material 3: wallpaper colors or your own seed, AMOLED, corner radius, font, card style |

## Features

- **Live status.** State, progress, layers, **time remaining** (where the printer reports it), nozzle and bed temperatures and a **temperature chart**.
- **Print control.** Pause, resume, cancel, set temperatures, light (FlashForge), axis moves and homing (where the protocol allows).
- **Files.** List files on the printer, upload from the phone, start a print.
- **Camera.** MJPEG stream; any printer can have its own camera URL.
- **Printer discovery.** FlashForge UDP broadcast plus a subnet port scan (Moonraker, FlashForge, OctoPrint).
- **Notifications.** A foreground monitor polls every 15 s and reports finished, stopped and failed prints. It stays idle while the app is open (FlashForge over TCP accepts a single client).
- **Several printers.** Add, edit and delete; a `host:port` form is accepted (`192.168.1.50:5000`).
- **Two themes.** Nothing-style or customizable Material 3.

## Supported printers

| Protocol | Models | What you enter |
|---|---|---|
| FlashForge TCP 8899 | Adventurer 3 / 3 Pro / 3 Lite / 4 / 4 Lite, Guider 2S, Finder, Dreamer, generic | IP only |
| FlashForge HTTP 8898 | Adventurer 5M / 5M Pro, AD5X | IP, serial number, check code (on the printer: Settings → Network, enable LAN mode) |
| Klipper / Moonraker | Creality K1 / K1 Max, Elegoo Neptune 4, anything with Moonraker | IP only (trusted clients) |
| OctoPrint | any | IP and API key (Settings → API → Application Keys) |

## How it works

```
UI (Compose) ─► DashboardViewModel ─► PrinterDriver ──┬─► FlashForgeLegacyDriver  (TCP 8899)
                                                       ├─► FlashForgeHttpDriver    (HTTP 8898)
PrintMonitorService ─► Notifier                        ├─► MoonrakerDriver
Discovery (UDP + port scan)                            └─► OctoPrintDriver
```

| Path | Purpose |
|---|---|
| `printer/PrinterDriver.kt`, `PrinterStatus.kt` | The common driver interface and the unified printer state |
| `printer/PrinterModel.kt` | Registry of supported models (`PrinterModels`) |
| `printer/flashforge/`, `moonraker/`, `octoprint/` | One driver and its parsers per protocol |
| `monitor/` | Foreground service and print-finished notifications |
| `net/Discovery.kt`, `net/Http.kt` | Printer discovery and the HTTP helper |
| `camera/MjpegStream.kt` | MJPEG decoding |
| `ui/` | Dashboard, printer list, settings; `ui/theme/` holds the shared theme tokens |
| `src/nothing/`, `src/material/` | Flavor-specific resources and `FlavorStyle` |
| `tools/mock-printer.js` | A mock Adventurer 4 for testing without a printer |

## Requirements

- Android 8.0 or newer (`minSdk 26`)
- A printer on the same network
- To build: Android SDK 35, JDK 17–21

## Download

Debug builds are attached to the [Releases](../../releases) page. They are signed with a debug key, so Android will ask you to allow installing from unknown sources.

## Build and install

```bash
git clone https://github.com/grskst2012-cell/FlashForgeRemote.git
cd FlashForgeRemote
```

1. Open the folder in Android Studio, pick the *nothingDebug* or *materialDebug* build variant, then **Run**.
2. Or build from the command line (`local.properties` must contain `sdk.dir=...`):

   ```bash
   ./gradlew assembleNothingDebug assembleMaterialDebug testNothingDebugUnitTest
   ```

   APKs end up in `app/build/outputs/apk/{nothing,material}/debug/`.

> Keep the project in a path without non-Latin characters. `gradle.properties` sets `android.overridePathCheck=true`, but unit tests in such a path fail with `ClassNotFoundException`.

## Setup

1. Connect the phone to the same Wi-Fi as the printer.
2. Tap **Add printer**, or run discovery to find printers on the network.
3. Choose the model and enter what the protocol needs (see the table above).
4. Allow notifications to get print-finished alerts.

To try the app without a printer:

```bash
node tools/mock-printer.js          # emulates an Adventurer 4 on port 8899
```

## Adding a model or protocol

A model is an entry in `PrinterModels` (`printer/PrinterModel.kt`). A new protocol is an implementation of `PrinterDriver`, a value in `PrinterProtocol` and a branch in `PrinterDrivers.create`.

## Limitations

- Everything except the Adventurer 4 is written from documentation and protocol descriptions and **has not been tried on real printers**. Bed sizes, temperatures and camera availability for newer models come from reference data.
- The UI builds and passes tests but has never been run in an emulator (no hardware virtualization), so it has not been checked visually.
- The background monitor can be killed by aggressive battery managers (Samsung, Xiaomi).
- LAN only; there is no remote access outside the home network.

## Roadmap

- [ ] Print history (local log, Moonraker `history/list`)
- [ ] Glance widget and a pause/cancel quick tile
- [ ] Bambu Lab driver (MQTT; needs Developer Mode on the printer)
- [ ] `ACCESS_LOCAL_NETWORK` permission flow before raising targetSdk to 37
- [ ] G-code layer preview, timelapse from camera frames

## Privacy

Printer addresses, API keys and access codes are stored only on the phone. The app talks only to printers on your local network; there is no analytics and no account.

## Research

The feature set is based on research into competing apps and printer protocols (written in Russian):
[`reports/Функции приложения для 3D-принтеров.md`](reports/%D0%A4%D1%83%D0%BD%D0%BA%D1%86%D0%B8%D0%B8%20%D0%BF%D1%80%D0%B8%D0%BB%D0%BE%D0%B6%D0%B5%D0%BD%D0%B8%D1%8F%20%D0%B4%D0%BB%D1%8F%203D-%D0%BF%D1%80%D0%B8%D0%BD%D1%82%D0%B5%D1%80%D0%BE%D0%B2.md)
(source notes are in `research_notes/`).

## License

[MIT](LICENSE). The Space Mono font (Nothing flavor) is under the SIL Open Font License 1.1, see `app/src/nothing/assets/licenses/`.
