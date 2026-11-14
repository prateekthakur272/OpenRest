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

Releases are made by hand.

1. Update `appVersion` in `desktopApp/build.gradle.kts`.
2. Run the tests and build the installer on a Mac:

   ```bash
   ./gradlew clean :shared:jvmTest :desktopApp:packageDmg
   ```

3. Copy the DMG to a clearly named file and make a checksum. Use `arm64` for an Apple-silicon Mac, `x64` for an Intel one (the DMG is built for the Mac it is built on):

   ```bash
   mkdir -p desktopApp/build/release && cd desktopApp/build/release
   cp ../compose/binaries/main/dmg/OpenRest-<version>.dmg OpenRest-<version>-macos-arm64.dmg
   shasum -a 256 OpenRest-<version>-macos-arm64.dmg > OpenRest-<version>-macos-arm64.dmg.sha256
   ```

4. Commit, then tag and push:

   ```bash
   git tag v<version>
   git push origin main v<version>
   ```

5. On GitHub open **Releases ▸ Draft a new release**, choose the tag, write or generate the release notes, drag in the DMG and its `.sha256` file, and click **Publish release**.

Build on each kind of Mac you want to support; an Intel DMG needs an Intel Mac.

The installers are **not signed or notarised** yet. macOS users may need to right-click ▸ **Open** the first time, and Windows may show a SmartScreen warning. Signing needs a developer certificate; it can be added to the `nativeDistributions` block.

## Regenerating the screenshots

The images in `docs/screenshots` are produced by a headless test against a local demo server, so they never show real data:

```bash
DOCS_SHOTS=$PWD/docs/screenshots ./gradlew :shared:jvmTest --tests '*DocsScreenshotsTest*' --rerun
```

Without `DOCS_SHOTS` the test does nothing, so it is safe in normal runs. The scenes are defined in `shared/src/jvmTest/kotlin/dev/prateekthakur/openrest/DocsScreenshotsTest.kt`.

## Changing the app icon

Edit `desktopApp/icons/icon.png` (1024×1024 with transparent corners), then regenerate `icon.icns` (macOS: make an `.iconset` folder and run `iconutil -c icns`) and `icon.ico` (any converter, include 16 to 256 px sizes).
