# ST-android-setting

English | [中文](README.md)

A third-party SillyTavern Android client. Built on the embedded Node.js runtime from [Sanitised/ST-android](https://github.com/Sanitised/ST-android), with native Android UI and extended features.

<img src="pics/ST-android-app-icon-original.svg" alt="App icon" width="120">

<img src="pics/ST-android-screenshot.png" alt="Screenshot" width="300">

> This is an independent third-party fork, not affiliated with or endorsed by SillyTavern or the upstream ST-android project.

## Relationship with Upstream

This project is forked from [Sanitised/ST-android](https://github.com/Sanitised/ST-android), inheriting its core capabilities:

- **Embedded Node.js runtime** — runs the SillyTavern server directly on Android without Termux or other tools
- **Bundled SillyTavern source** — works out of the box with zero setup

On top of that, this project focuses on:

- **Native Compose UI** — character management, settings, and tools built with Jetpack Compose + Material 3, moving beyond a pure WebView approach
- **Bottom navigation** — five tabs: Home / Chat / Characters / Tools / Settings
- **Character management migration** — character list, detail, and edit screens progressively migrated to native implementation
- **Dashboard home** — status card, recent chats, quick actions

## Features

- One-click SillyTavern on Android 8.0+ (arm64)
- Native character management (list, detail, edit, tags, filters)
- Import/export system, compatible with both this app's backup format and SillyTavern's native backups
- Custom SillyTavern versions: any version, branch, repo, or ZIP archive
- Dark/light theme
- Auto-open browser when server is ready

## Privacy

- No telemetry of any kind.
- Works in Private Space, Secure Folder, and secondary profiles.
- Minimal network calls: opt-in GitHub release checks, npm installs, and GitHub downloads for custom ST versions. All other traffic comes from SillyTavern itself.
- All chats, characters, and settings stay local.

## Installation

Download the APK from [Releases](https://github.com/5151561/ST-android-setting/releases/latest) (allow installs from your browser/files app if Android asks).

## Data Transfer

Transfer data from SillyTavern on Termux or PC. Supports `.tar.gz`, `.tar`, and `.zip` archives with automatic format detection.

### Option 1: SillyTavern User Backup

1. In your old SillyTavern: **User Settings** → **Account** → **Download Backup**
2. In this app: stop the server → **Manage ST** → **Import Data** → select the backup file

### Option 2: One-liner Export Script (Termux / Linux)

```bash
bash <(curl -sSf https://raw.githubusercontent.com/Sanitised/ST-android/master/tools/export_to_st_android.sh)
```

If your SillyTavern folder is not in a standard location, first `cd ./my-sillytavern`.

Then in the app: stop the server → **Manage ST** → **Import Data** → select the backup file.

### Option 3: Manual Archive

Archive structure:

```
st_backup/
├── config.yaml
└── data/
```

```bash
mkdir st_backup
cp /path/to/sillytavern/config.yaml st_backup/
cp -r /path/to/sillytavern/data st_backup/
tar -czf st_backup.tar.gz st_backup/
```

On Termux, copy to Downloads:

```bash
termux-setup-storage
cp st_backup.tar.gz ~/storage/downloads/
```

## Build

Requires Docker (and Git). Tested on Linux only.

```bash
git clone https://github.com/5151561/ST-android-setting
cd ST-android-setting
git submodule update --init --recursive
./ci/scripts/build_apk_docker.sh
```

The first build takes around 2–3 hours (compiling Node.js from source). Subsequent builds are much faster.

Output: `app/build/outputs/apk/debug/app-debug.apk`

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## Acknowledgements

- [Sanitised/ST-android](https://github.com/Sanitised/ST-android) — upstream project providing the embedded Node.js runtime and core architecture
- [SillyTavern](https://github.com/SillyTavernAI/SillyTavern) — frontend chat interface
