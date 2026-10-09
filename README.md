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

- **Build & generated-app fixes (v1.0.42.1)** — the v1.0.42 TTS work and today's fixes now coexist in one build: WebView **speech works again** in built apps (the `@JavascriptInterface` annotation is promoted to runtime-visible and shrinking can no longer strip it), **D8 no longer crashes** (the bundled R8/D8 is never minified), the **project version really bumps on every build** (re-read from the project and stripped from the manifest), the **Material3 theme paints the top bar with the project's colour** (and never emits attributes `material-1.13.0` does not declare), the **Toolbar toggle is honoured even with a custom `AndroidManifest.xml`**, the **editor preview stays in sync** when a screen's options change, and a **copy button** was added to the compile log — all on top of v1.0.42's always-audible TTS bridge (deduplicated, migrated in already-patched projects, with on-device diagnostics). Details and honest limits: [`REGRESSION-NOTES.md`](REGRESSION-NOTES.md) · [`CHANGELOG.md`](CHANGELOG.md).
- **Build & generated-app fixes (v1.0.41.1)** — WebView **speech works again** in built apps (the `@JavascriptInterface` annotation is promoted to runtime-visible and shrinking can no longer strip it), **D8 no longer crashes** (the bundled R8/D8 is never minified), the **project version really bumps on every build** (re-read from the project and stripped from the manifest), the **Material3 theme paints the top bar with the project's colour** (and never emits attributes `material-1.13.0` does not declare), the **Toolbar toggle is honoured even with a custom `AndroidManifest.xml`**, the **editor preview stays in sync** when a screen's options change, and a **copy button** was added to the compile log. Details and honest limits: [`REGRESSION-NOTES.md`](REGRESSION-NOTES.md) · [`CHANGELOG.md`](CHANGELOG.md).
- **Code Viewer save fix (v1.0.41)** — with edit mode on, saving `AndroidManifest.xml` now really saves it (before it showed *"This source cannot be saved from Code Viewer."* and saved nothing): the edit is written to `.AndroidSCode/data/<scId>/files/AndroidManifest.xml`, that file becomes the project's manifest (the generator prefers it over the one assembled on the fly) and the viewer's notices are localised into English, Spanish and Portuguese.
- **TTS in built apps (v1.0.40)** — a `WebView` bridge (`AndroidBridge` with `speak`/`stop`) plus a `window.speechSynthesis` shim makes any compiled app with a WebView speak, just like in the browser, with TTS engine visibility on Android 11+ and a fallback engine when the default one fails; also per-project **WebView settings**, **copy design & logic between screens** with the editor's brush tool, a localised **AI chat** (EN/ES/PT), a **Build** button on the home hero, a fixed light mode, and build settings (D8/Java 8) saved instantly.
- **Redesigned home** — a "continue where you left off" card, quick actions (new, import, templates) and clean project cards, on a neutral + green palette.
- **Multi-language** — English, Spanish and Brazilian Portuguese, switchable from **Settings → Language**.
- **Redesigned editor** — a modern block palette with search and colour-coded categories, a cleaner top bar, and an Events section with cards and descriptions, in the app's green.
- **Visual design editor** — drag & drop widgets, edit the XML directly and preview screens, with editable corner radius, stroke, 2-colour gradient (vertical, horizontal and diagonal), layout_gravity on any widget and a glass mode, that render the same in the canvas, the preview and the built app.
- **Logic editor** — blocks for fast prototyping, plus raw Java/Kotlin when you need full control.
- **Resource manager** — images, sounds, fonts, icons, collections and custom blocks per project.
- **Local libraries & dependencies** — bundle your own `.jar`/`.aar` or resolve Maven artifacts.
- **Build & sign** — produce a signed APK or AAB, manage your keystores and install the result
  straight from the app.
- **Project backup** — export and restore projects as portable `.swb` files.
- **Extras** — Kotlin compiler, Flutter toolchain support (bundled Dart runtime + AOT backend, kept in step with the latest Dart SDK) and a Local AI Manager with token-by-token streaming.

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

## Acknowledgements

Android SCode is an independent **derivative work based on Sketchware** (and its community forks).
It is **not affiliated with, sponsored or endorsed by** the original project. All trademarks belong
to their respective owners, and the notices and attributions required by the original license are
preserved — see [LICENSE.md](LICENSE.md).

## Credits

Created and maintained by **Andrés Mag**.

Home redesign (**v1.0.35**) — design collaboration: **[@azkafirley](https://github.com/azkafirley)**.

## License

See **[LICENSE.md](LICENSE.md)**. This project derives from an open, source-available codebase; all
required notices and attributions are preserved.

<div align="center"><sub>Android SCode · build Android apps from your phone</sub></div>
