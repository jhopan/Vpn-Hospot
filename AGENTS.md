# VPN Hospot — AGENTS.md

Android app (Java) that runs HTTP and SOCKS5 proxy servers on the device and routes their traffic through the active VPN/TUN interface, so other devices on a Wi-Fi hotspot or USB tethering use the VPN's exit IP. Single-module Gradle project (`:app`), namespace `com.jhopanstore.vpnhospot`.

## Dev environment

- **JDK 17** (Temurin in CI). Source/target compatibility pinned to `VERSION_17`.
- Gradle 8.9 via wrapper; AGP 8.7.3. Use `./gradlew` (or `gradlew.bat` on Windows) — do not invoke a system `gradle`.
- `compileSdk`/`targetSdk` = 34, `minSdk` = 23.
- `local.properties` (SDK path) is gitignored and required to build — set `sdk.dir` locally; it is not checked in.
- `gradle.properties` sets `android.disableJdkImageTransform=true` and `-Xmx2048m`. Leave these alone; removing the JDK-image-transform flag breaks the build on this toolchain.

## Build & test

- Debug APK: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/`
- Release APKs (3 ABI splits): `./gradlew assembleRelease` → `app/build/outputs/apk/release/` (`app-arm64-v8a-release.apk`, `app-armeabi-v7a-release.apk`, `app-universal-release.apk`)
- The CI workflow (`.github/workflows/build-release.yml`) runs `./gradlew :app:assembleRelease` and renames APKs to `VpnHospot-<version>-<abi>.apk`.
- **No test suite, no lint config, no test dependencies.** There is nothing to run — `./gradlew test` is a no-op here. Do not invent test commands.
- APK splits are always on (`splits.abi`); there is no single-fat-apk build type.

## Release & signing

- Release builds are minified + resource-shrunk with `proguard-android-optimize.txt` + `app/proguard-rules.pro`.
- ProGuard keeps only the manifest-referenced entry points: `ProxyService` and `MainActivity` (plus enum members). Any new class invoked only reflectively or from JNI/native must be added to `proguard-rules.pro` or it will be stripped.
- **The release build type is signed with the debug signing config** (`signingConfig = signingConfigs.getByName("debug")`). This is intentional for this repo's distribution model — do not "fix" it unless asked.
- Releases are cut by pushing a `v*` tag (e.g. `v1.0.0`); the workflow extracts the version from the tag name and creates a GitHub Release. `workflow_dispatch` also works.

## Code layout

All Java lives in `app/src/main/java/com/jhopanstore/vpnhospot/`:

- `SplashActivity` (launcher) → `MainActivity` → `AboutActivity`. Activities are not exported except `SplashActivity`.
- `ProxyService` — foreground service (`foregroundServiceType="dataSync"`); owns the `ProxyServer` instances, notification, and traffic counter. Started/stopped via `ACTION_START`/`ACTION_STOP` intents carrying port extras.
- `ProxyServer` — generic accept-loop wrapper; takes a `ProxyServer.Handler` implementation. One instance for HTTP, one for SOCKS5.
- `HttpProxyHandler`, `Socks5ProxyHandler` — the protocol handlers. SOCKS5 supports TCP CONNECT + UDP ASSOCIATE with a 60s keep-alive and 4-layer fallback session mapping.
- `InterfaceDetector` — picks the VPN/TUN interface for binding (priority: VPN > active network > cellular).
- `BytePump`, `TrafficCounter` — byte transfer + live stats.
- Default ports: HTTP `8080`, SOCKS5 `1080` (both configurable in-app).

## Conventions

- **Java, not Kotlin.** No Kotlin plugin, no `.kt` files. New code should be `.java`.
- Package-private by default; classes are `final` unless extended (e.g. `ProxyServer` is package-private final). `ProxyService` is public only because it's a Service entry point.
- Fields are `volatile` when read/written across the accept thread and main thread (`running`, port fields, `lastMessage`). Preserve this for any new cross-thread state.
- Logging: `private static final String TAG = "<ClassName>";` + `Log.e/w/i/d(TAG, ...)`. Some trace logs are committed but commented out — leave them; they're used for debugging in place.
- UI strings mix English and Indonesian (e.g. `"Proxy belum berjalan"`). Match the surrounding language of the file you're editing rather than translating.
- Commit messages follow Conventional Commits: `feat:`, `fix:`, `docs:` (see `git log`). Scope is optional.

## Pitfalls

- `local.properties` must exist locally (SDK dir); CI injects it via `setup-java` + the Gradle action. A fresh clone will not build until you create it.
- Runtime needs the `INTERNET`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC`, and `POST_NOTIFICATIONS` permissions (all declared in `AndroidManifest.xml`). `POST_NOTIFICATIONS` is runtime-granted on API 33+.
- The proxy binds to `0.0.0.0` on the chosen ports; if another app holds `8080` or `1080`, the `ServerSocket` bind throws. These ports are configurable in the UI to work around conflicts.
- UDP stability depends on the upstream VPN provider and remote game server — failures here are environmental, not app bugs.
