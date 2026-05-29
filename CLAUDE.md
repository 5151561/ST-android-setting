# CLAUDE.md

给我讲中文。




This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Project Is

ST-android is an Android app that runs SillyTavern (a Node.js chat application) on-device. It bundles Node.js and SillyTavern source as git submodules, launches a local Node server as a foreground service, and provides a native Compose UI shell around it. Chat is handled by SillyTavern's web frontend inside an embedded WebView; other screens (characters, settings, tools) are being migrated to native Compose.

## Build & Test

```bash
# Build debug APK (requires Android SDK + JDK 17)
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# Run all unit tests
./gradlew test

# Run a single test class
./gradlew testDebugUnitTest --tests "io.github.sanitised.st.api.TavernCoreClientTest"

# Docker build (Linux only, compiles Node from source — first build ~2-3 hours)
git submodule update --init --recursive
./ci/scripts/build_apk_docker.sh
```

The project has no lint configuration beyond the defaults. No instrumented/Android tests exist — only local JVM unit tests under `app/src/test/`.

`gradle.properties` pins `org.gradle.java.home` to a local VS Code JDK path. This is machine-specific and should not be changed in commits.

## Architecture

**Single-module Gradle project** (`app/`). Package: `io.github.sanitised.st`.

### Core runtime

- `NodeService` — Android foreground service that spawns the Node.js process running SillyTavern. Communicates status via `NodeStatusListener` callbacks.
- `NodePayload` — Extracts bundled Node binary and SillyTavern source from APK assets on first run. Manages custom ST installations (GitHub repos/branches/ZIP archives).
- `MainViewModel` — Central ViewModel. Delegates to `BackupManager`, `CustomInstallManager`, `UpdateManager`, and `BatteryPromptManager`. Exposes most UI state as `MutableState` fields.
- `AppPaths` — Single source of truth for all filesystem paths (`stDir`, `dataDir`, `configDir`, etc.).

### UI layers

- **Navigation**: `MainActivity` hosts a `NavHost` with 5 bottom tabs defined in `ui/navigation/STNavGraph.kt` (`STRoutes`). Routes: Home, Chat, Characters, Tools, Settings (plus sub-routes for logs, config, legal, manage-ST).
- **Chat tab**: `ui/webview/ChatWebViewScreen` loads SillyTavern's web UI in an Android WebView. `STAndroidBridge` exposes `@JavascriptInterface` methods (app info, theme, clipboard) to the JS side.
- **Characters tab**: Native Compose screens — `CharacterListScreen`, `CharacterDetailScreen`, `CharacterEditScreen`. Supporting logic in `CharacterEditTools`, `CharacterTagTools`, `CharacterFilters`.
- **Home tab**: `UiApp.kt` (`STAndroidApp` composable) — dashboard with status card, recent chats/characters, quick-action buttons. `M1HubScreens.kt` has `DashboardStatusCard` and `DashboardLibrarySections`.
- **Theme**: `ui/theme/STTheme.kt` defines `STAppTheme` with custom design tokens (`STColors`, `STSpacing`, `STRadius`, `STTypography`) provided via `CompositionLocal`. Both light and dark schemes exist.

### Data access — dual path

- **API path**: `api/TavernCoreClient` (implements `TavernCoreApi`) talks to the local SillyTavern HTTP server via OkHttp. Handles characters, chats, tags, import/export. Uses SnakeYAML for JSON parsing (not Gson/Moshi). CSRF tokens are fetched automatically.
- **Local file path**: `data/LocalTavernLibraryReader` reads character PNGs and chat JSONLs directly from the filesystem under `data/<user>/`. Used as a fallback/cache when the server isn't running.

The M2 milestone is migrating character management to primarily use the API path, with local file reading as fallback.

### Git submodules

- `SillyTavern/` — upstream SillyTavern source (bundled unmodified into the APK)
- `node/` — Node.js source (compiled for Android arm64 during Docker builds)

## Key Conventions

- UI language is Chinese . String resources are in `res/values/strings.xml` and `res/values-zh/strings.xml`.
- Compose is used with Material 3 (`androidx.compose.material3`). No Compose compiler plugin — uses `kotlinCompilerExtensionVersion = "1.5.14"` in build config.
- JSON construction in `TavernCoreClient` is hand-rolled (`jsonObject()`, `jsonValue()`, `quoteJson()`) — no JSON library dependency for serialization.
- The debug build variant uses applicationId suffix `.dev` and app name "ST dev".
- `minSdk = 26` (Android 8.0), `targetSdk = 36`.
