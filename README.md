<div align="center">

<img src="assets/Android-SCode.png" width="132" alt="Android SCode">

# Android SCode

**Build real Android apps directly on your phone.**

Design, code, build and sign Android applications without a computer.

[![Latest release](https://img.shields.io/github/v/release/aurenox-global/Android-SCode?label=release&color=3DDC84)](https://github.com/aurenox-global/Android-SCode/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/aurenox-global/Android-SCode/total?color=3DDC84)](https://github.com/aurenox-global/Android-SCode/releases)
[![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84)](#requirements)
[![Telegram](https://img.shields.io/badge/telegram-join%20the%20group-2CA5E0?logo=telegram&logoColor=white)](https://t.me/AndroidSCode)

[Website](https://aurenox-global.github.io/Android-SCode/) · [Releases](https://github.com/aurenox-global/Android-SCode/releases) · [Español](README.es.md)

</div>

---

## Overview

**Android SCode** is an Android IDE that runs on the device itself. It provides a visual layout
editor, a block‑based logic editor, full Java/Kotlin code editing, resource management, dependency
resolution and a complete build pipeline that produces a **signed APK or AAB** — all offline, on
your phone.

| | |
|---|---|
| **Application ID** | `com.ascode.android` |
| **Minimum Android** | 8.0 (API 26) |
| **Target / Compile SDK** | 35 / 36 |
| **Distribution** | One **universal APK** (all ABIs) |

## Features

- **Visual design editor** — drag & drop widgets, edit the XML directly and preview screens.
- **Logic editor** — blocks for fast prototyping, plus raw Java/Kotlin when you need full control.
- **Resource manager** — images, sounds, fonts, icons, collections and custom blocks per project.
- **Local libraries & dependencies** — bundle your own `.jar`/`.aar` or resolve Maven artifacts.
- **Build & sign** — produce a signed APK or AAB, manage your keystores and install the result
  straight from the app.
- **Project backup** — export and restore projects as portable `.swb` files.
- **Extras** — Kotlin compiler, Flutter toolchain support and a Local AI Manager.

## Download

Grab the latest **universal APK** (works on `arm64-v8a`, `armeabi-v7a`, `x86_64` and `x86`):

**[→ Latest release](https://github.com/aurenox-global/Android-SCode/releases/latest)**

> **Note:** the app uses its own package name and signature, so it installs as a **new
> application** — it does not update over installs of other packages. On first launch it
> automatically imports existing projects from the legacy `.sketchware` folder into `.AndroidSCode`.

## Requirements

**To install:** Android 8.0+ and permission to install apps from unknown sources.

**To build from source:**

- JDK **17**
- Android SDK with **compileSdk 36** and the matching build-tools
- (The Gradle wrapper is included — no separate Gradle install needed.)

## Build from source

```bash
git clone https://github.com/aurenox-global/Android-SCode.git
cd Android-SCode

# Point to your SDK (or set ANDROID_HOME)
echo "sdk.dir=$HOME/Android/Sdk" > local.properties

# Single universal APK
./gradlew :app:assembleRelease
```

Output:

```
app/build/outputs/apk/release/
├── app-universal-release.apk   # one APK, all ABIs
├── app-arm64-v8a-release.apk
├── app-armeabi-v7a-release.apk
├── app-x86-release.apk
└── app-x86_64-release.apk
```

### Signing

Release builds are signed with the keystore included in this repository (`ascode.keystore`,
alias `ascode`), so consecutive releases install over each other. To sign with your own key,
set the environment variables before building:

```bash
export RELEASE_STORE_FILE=/path/to/your.keystore
export RELEASE_STORE_PASSWORD=****
export RELEASE_KEY_ALIAS=your-alias
export RELEASE_KEY_PASSWORD=****
```

> Environment variables are the only override: `gradle.properties` is intentionally ignored so a
> global configuration cannot change the release signature by accident.

## Project structure

| Path | Description |
|---|---|
| `app/` | Android application module (sources, resources, bundled libraries) |
| `app/src/main/java/com/ascode/android/` | Application code |
| `app/src/main/java/com/besome/sketch/` | Editor UI (activities, adapters, editor views) |
| `app/src/main/java/mod/`, `a/a/a/` | Compiler, project model and legacy support code |
| `docs/` | Project website (GitHub Pages) and development notes |
| `gradle/libs.versions.toml` | Version catalog: SDK, plugins and dependencies |
| `scripts/` | Maintenance and build helper scripts |
| `ascode.keystore` | Default release signing key |

## Contributing

This repository has a **single maintainer** and **every external contribution must be approved by
the maintainer before it is merged**.

- Changes land through **pull requests**; `main` is protected and a PR requires the maintainer's
  review. Opening a PR does not guarantee it will be merged.
- Keep PRs **small and focused**, and always describe **how you tested** them.
- Please read **[CONTRIBUTING.md](CONTRIBUTING.md)** before opening a PR.

## Community

Questions, ideas and bug reports:

- **Telegram group:** https://t.me/AndroidSCode
- **Issues:** https://github.com/aurenox-global/Android-SCode/issues

## Credits

Created and maintained by **Andrés Mag**.

## License

See **[LICENSE.md](LICENSE.md)**. This project derives from an open, source-available codebase; all
required notices and attributions are preserved.

<div align="center"><sub>Android SCode · build Android apps from your phone</sub></div>
