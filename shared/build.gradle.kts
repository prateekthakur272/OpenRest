plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.buildConfig)
}

kotlin {
    jvm()
    sourceSets {
        jvmMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.compose.iconsExtended)
            implementation(libs.snakeyaml)
            implementation(libs.graal.polyglot)
            implementation(libs.graal.js)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
            implementation(compose.desktop.currentOs)
        }
    }
}

// BuildConfig: constants generated at build time from gradle.properties (like Android's BuildConfig), so the app and the installer share one source.
val appVersion: String by project
val appVendor: String by project
val appDescription: String by project
val appHomepage: String by project
val appDeveloperUrl: String by project
buildConfig {
    packageName("dev.prateekthakur.openrest")
    className("BuildConfig")
    buildConfigField("VERSION", appVersion)
    buildConfigField("VENDOR", appVendor)
    buildConfigField("DESCRIPTION", appDescription)
    buildConfigField("HOMEPAGE", appHomepage)
    buildConfigField("DEVELOPER_URL", appDeveloperUrl)
}

tasks.withType<Test>().configureEach {
    val home = layout.buildDirectory.dir("test-home").get().asFile
    systemProperty("openrest.home", home.absolutePath)
    doFirst { home.deleteRecursively() }
}
