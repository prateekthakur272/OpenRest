import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.buildConfig)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}


// App metadata, kept here in the app module like Android's applicationId / versionName.
// It feeds the installer below and the generated BuildConfig that main() hands to the shared UI (About and Developer dialogs).
val appName = "OpenRest"                                      // the app's label: OpenRest.app, the Dock, the menu bar, the installer
val appBundleId = "dev.prateekthakur.openrest"                // macOS identity; do not change it after a release
val appVersion = "1.0.0"
val appVendor = "Prateek Thakur"
val appDescription = "An open, file-based API client: requests, environments and workflows that live in plain folders."
val appHomepage = "https://github.com/prateekthakur272/OpenRest"
val appDeveloperUrl = "https://github.com/prateekthakur272"

buildConfig {
    packageName("dev.prateekthakur.openrest")
    className("BuildConfig")
    buildConfigField("APP_NAME", appName)
    buildConfigField("BUNDLE_ID", appBundleId)
    buildConfigField("VERSION", appVersion)
    buildConfigField("VENDOR", appVendor)
    buildConfigField("DESCRIPTION", appDescription)
    buildConfigField("HOMEPAGE", appHomepage)
    buildConfigField("DEVELOPER_URL", appDeveloperUrl)
}

compose.desktop {
    application {
        mainClass = "dev.prateekthakur.openrest.MainKt"
        // macOS labels the Dock and the menu bar after the Java process unless told otherwise (matters for `run`; a packaged app uses its Info.plist)
        jvmArgs += listOf("-Xdock:name=$appName", "-Dapple.awt.application.name=$appName")

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = appName                            // the app's label: OpenRest.app, the Dock, the menu bar, the DMG
            packageVersion = appVersion                    // the installer version
            vendor = appVendor
            description = appDescription
            macOS {
                bundleID = appBundleId                       // identity, not a label: keep it so settings and installs carry over
                iconFile.set(project.file("icons/icon.icns"))
            }
            windows { iconFile.set(project.file("icons/icon.ico")) }
            linux { iconFile.set(project.file("icons/icon.png")) }
        }
    }
}