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

The values are `val`s near the top of `desktopApp/build.gradle.kts`, the way an Android app keeps `applicationId` and `versionName` in its app module:

| Setting | Meaning |
|---|---|
| `appName` | The app's label: `OpenRest.app`, the Dock, the menu bar, the installer name (`packageName`) |
| `appBundleId` | The macOS identity (`bundleID`). Do not change it after a release; settings and installs follow it |
| `appVersion`, `appVendor`, `appDescription` | The installer's version, vendor and description |
| `appHomepage`, `appDeveloperUrl` | Links shown in the Developer dialog |
| `iconFile` | `desktopApp/icons/icon.icns` (macOS), `icon.ico` (Windows), `icon.png` (Linux) |

The same module also generates a `BuildConfig` object from them (`APP_NAME`, `BUNDLE_ID`, `VERSION`, `VENDOR`, `DESCRIPTION`, `HOMEPAGE`, `DEVELOPER_URL`). `main()` copies it into `AppInfo.meta`, which the shared UI reads for the About and Developer dialogs.


## Releasing a new version

1. Update `appVersion` in `desktopApp/build.gradle.kts`.
2. Commit and push to `main`.
3. Push a tag that matches the version, with a leading `v`:

   ```bash
   git tag v1.0.1
   git push origin v1.0.1
   ```

4. The **Release (macOS)** workflow (`.github/workflows/release-macos.yml`) does the rest, see below.

### The release pipeline

Pushing a `v*` tag runs three jobs:

| Job | What it does |
|---|---|
| **verify** | Fails early if the tag does not match `appVersion`, then runs `./gradlew :shared:jvmTest` |
| **dmg** | Builds the DMG on an Apple-silicon runner (`arm64`) and an Intel runner (`x64`), names each `OpenRest-<version>-macos-<arch>.dmg` and writes a `.sha256` checksum |
| **release** | Creates the GitHub release for the tag, with generated release notes and every DMG and checksum attached |

A tag with a hyphen, such as `v1.1.0-rc1`, is published as a **pre-release**; it is checked against `1.1.0`. If a run fails, fix the cause, delete the tag (`git push origin :refs/tags/v1.0.1`, `git tag -d v1.0.1`) and tag again.

You can rehearse the build without publishing by running the **dmg** steps locally (`./gradlew :desktopApp:packageDmg`).

The installers are **not signed or notarised** yet. macOS users may need to right-click ▸ **Open** the first time, and Windows may show a SmartScreen warning. Signing needs a developer certificate; it can be added to the `nativeDistributions` block.

## Regenerating the screenshots

The images in `docs/screenshots` are produced by a headless test against a local demo server, so they never show real data:

```bash
DOCS_SHOTS=$PWD/docs/screenshots ./gradlew :shared:jvmTest --tests '*DocsScreenshotsTest*' --rerun
```

Without `DOCS_SHOTS` the test does nothing, so it is safe in normal runs. The scenes are defined in `shared/src/jvmTest/kotlin/dev/prateekthakur/openrest/DocsScreenshotsTest.kt`.

## Changing the app icon

Edit `desktopApp/icons/icon.png` (1024×1024 with transparent corners), then regenerate `icon.icns` (macOS: make an `.iconset` folder and run `iconutil -c icns`) and `icon.ico` (any converter, include 16 to 256 px sizes).
