import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "dev.prateekthakur.openrest.MainKt"
        // macOS labels the Dock and the menu bar after the Java process unless told otherwise (matters for `run`; a packaged app uses its Info.plist)
        jvmArgs += listOf("-Xdock:name=OpenRest", "-Dapple.awt.application.name=OpenRest")

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "OpenRest"                       // the app's label: OpenRest.app, the Dock, the menu bar, the DMG
            packageVersion = "1.0.0"                       // keep in step with AppInfo.VERSION (a test checks this)
            macOS {
                bundleID = "dev.prateekthakur.openrest"    // identity, not a label: keep it so settings and installs carry over
                iconFile.set(project.file("icons/icon.icns"))
            }
            windows { iconFile.set(project.file("icons/icon.ico")) }
            linux { iconFile.set(project.file("icons/icon.png")) }
        }
    }
}