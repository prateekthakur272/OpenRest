plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
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

tasks.withType<Test>().configureEach {
    val home = layout.buildDirectory.dir("test-home").get().asFile
    systemProperty("openrest.home", home.absolutePath)
    doFirst { home.deleteRecursively() }
}
