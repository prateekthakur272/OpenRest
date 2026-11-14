# Building and releasing

## Requirements

- **JDK 17 or newer** (the installers bundle their own runtime, built with `jpackage`).
- Nothing else: Gradle is included as `./gradlew`.
- To build an installer you must be on the target operating system (a DMG needs macOS, an MSI needs Windows, a DEB needs Linux).

## Everyday commands

```bash
./gradlew :desktopApp:run          # run the app
./gradlew :shared:jvmTest          # run all tests
./gradlew :shared:compileKotlinJvm # compile only
```

## Installers

```bash
./gradlew :desktopApp:packageDmg   # macOS     → desktopApp/build/compose/binaries/main/dmg/OpenRest-1.0.0.dmg
./gradlew :desktopApp:packageMsi   # Windows   → …/msi/
./gradlew :desktopApp:packageDeb   # Linux     → …/deb/
```

All settings are in `desktopApp/build.gradle.kts`:

| Setting | Meaning |
|---|---|
| `packageName` | The app's label: `OpenRest.app`, the Dock, the menu bar, the installer name |
| `packageVersion` | The installer version. Keep it equal to `AppInfo.VERSION` (a test checks this) |
| `macOS { bundleID }` | The macOS identity. Do not change it after a release; settings and installs follow it |
| `iconFile` | `desktopApp/icons/icon.icns` (macOS), `icon.ico` (Windows), `icon.png` (Linux) |

## Releasing a new version

1. Update `packageVersion` in `desktopApp/build.gradle.kts` and `AppInfo.VERSION` in `features/shell/Menus.kt`.
2. Run `./gradlew :shared:jvmTest`.
3. Build the installer on each target system.
4. Tag the commit and attach the installers to the release.

The installers are **not signed or notarised** yet. macOS users may need to right-click ▸ **Open** the first time, and Windows may show a SmartScreen warning. Signing needs a developer certificate; it can be added to the `nativeDistributions` block.

## Regenerating the screenshots

The images in `docs/screenshots` are produced by a headless test against a local demo server, so they never show real data:

```bash
DOCS_SHOTS=$PWD/docs/screenshots ./gradlew :shared:jvmTest --tests '*DocsScreenshotsTest*' --rerun
```

Without `DOCS_SHOTS` the test does nothing, so it is safe in normal runs. The scenes are defined in `shared/src/jvmTest/kotlin/dev/prateekthakur/openrest/DocsScreenshotsTest.kt`.

## Changing the app icon

Edit `desktopApp/icons/icon.png` (1024×1024 with transparent corners), then regenerate `icon.icns` (macOS: make an `.iconset` folder and run `iconutil -c icns`) and `icon.ico` (any converter, include 16 to 256 px sizes).
