package dev.prateekthakur.openrest

import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.storage.*
import dev.prateekthakur.openrest.features.shell.*

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.*
import dev.prateekthakur.openrest.features.shell.OpenRestApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PreferencesTest {
    private val out = File(System.getProperty("shots") ?: "/private/tmp/claude-501/-Users-ent-0554-IdeaProjects-OpenRest/e2e29356-c9b9-40cc-a6f8-baf7c71fe55c/scratchpad/shots").also { it.mkdirs() }
    private fun shot(t: DesktopComposeUiTest, name: String) {
        val img = t.onAllNodes(isRoot()).onFirst().captureToImage()
        File(out, "$name.png").writeBytes(org.jetbrains.skia.Image.makeFromBitmap(img.asSkiaBitmap()).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes)
    }

    @Test fun preferencesAreSectionedSearchableAndSavedAsYouGo() = runDesktopComposeUiTest(1360, 860) {
        AppStore.saveState(AppStore.SavedState(emptyList(), emptyMap()))
        val state = AppState(CoroutineScope(Dispatchers.Default))
        state.updateSettings(AppSettings())
        setContent { OpenRestApp(state) }
        waitForIdle()
        state.modal = Modal.Settings; waitForIdle()
        shot(this, "prefs-1-general")

        // every section is one click away
        listOf("General", "Appearance", "Requests", "Network", "Proxy", "Shortcuts", "Data").forEach { onNodeWithTag("nav-$it").assertExists() }
        onNodeWithTag("pref-autosave").assertExists()
        assertFalse(state.settings.autoSave)
        onNodeWithTag("pref-autosave").performClick(); waitForIdle()
        assertTrue(state.settings.autoSave)                                              // applied immediately...
        assertTrue(AppStore.loadSettings().autoSave)                                      // ...and saved

        // appearance: theme, size, layout
        onNodeWithTag("nav-Appearance").performClick(); waitForIdle()
        shot(this, "prefs-2-appearance")
        onNodeWithTag("pref-theme-Light").performClick(); waitForIdle()
        assertEquals(ThemeMode.Light, state.settings.theme)
        onNodeWithTag("pref-theme-Dark").performClick(); waitForIdle()
        assertEquals(ThemeMode.Dark, state.settings.theme)
        onNodeWithTag("pref-theme-System").performClick(); waitForIdle()                 // follow the operating system
        assertEquals(ThemeMode.System, state.settings.theme)
        assertEquals(ThemeMode.System, AppStore.loadSettings().theme)
        onNodeWithText("Follows your operating system", substring = true).assertExists()
        shot(this, "prefs-2b-system-theme")
        onNodeWithTag("pref-theme-Dark").performClick(); waitForIdle()
        onNodeWithTag("pref-size-up").performClick(); waitForIdle()
        assertEquals(14, state.settings.fontSize)
        onNodeWithText("107%", substring = true).assertExists()
        onNodeWithTag("pref-size-down").performClick(); onNodeWithTag("pref-size-down").performClick(); waitForIdle()
        assertEquals(12, state.settings.fontSize)
        onNodeWithTag("pref-layout-Side by side").performClick(); waitForIdle()
        assertFalse(state.settings.verticalLayout)

        // requests: redirects reveal their limit
        onNodeWithTag("nav-Requests").performClick(); waitForIdle()
        onNodeWithText("Maximum redirects").assertExists()
        onNodeWithTag("pref-redirects").performClick(); waitForIdle()
        assertFalse(state.settings.followRedirects)
        onNodeWithText("Maximum redirects").assertDoesNotExist()
        onNodeWithTag("pref-redirects").performClick(); waitForIdle()

        // network: turning verification off is called out
        onNodeWithTag("nav-Network").performClick(); waitForIdle()
        onNodeWithTag("pref-ssl").performClick(); waitForIdle()
        assertFalse(state.settings.sslVerify)
        onNodeWithText("connections are not verified", substring = true).assertExists()
        onNodeWithTag("pref-ssl").performClick(); waitForIdle()

        // search runs across every section; the proxy host and port only appear once a proxy is switched on
        onNode(hasSetTextAction() and hasText("Search settings")).performTextInput("proxy"); waitForIdle()
        onNodeWithText("Use an HTTP proxy").assertExists()
        onNodeWithText("Proxy host").assertDoesNotExist()
        onNodeWithTag("pref-proxy").performClick(); waitForIdle()
        assertTrue(state.settings.proxyEnabled)
        onNodeWithText("Proxy host").assertExists()
        shot(this, "prefs-3-search")

        // data: reset needs a second, explicit click
        onNode(hasSetTextAction() and hasText("proxy")).performTextReplacement(""); waitForIdle()
        onNodeWithTag("nav-Data").performClick(); waitForIdle()
        onNodeWithTag("pref-reset").performClick(); waitForIdle()
        assertTrue(state.settings.proxyEnabled)                                          // nothing changed yet
        onNodeWithTag("pref-reset-confirm").performClick(); waitForIdle()
        assertEquals(AppSettings(), state.settings)
        assertEquals(AppSettings(), AppStore.loadSettings())
        shot(this, "prefs-4-data")
        onNodeWithTag("nav-Shortcuts").performClick(); waitForIdle()
        onNodeWithText("Command palette").assertExists()
        shot(this, "prefs-5-shortcuts")
    }

    @Test fun systemThemeFollowsTheOperatingSystemAndOldFilesMigrate() {
        // the rule
        assertTrue(ThemeMode.System.isDark(true)); assertFalse(ThemeMode.System.isDark(false))
        assertFalse(ThemeMode.Light.isDark(true)); assertTrue(ThemeMode.Dark.isDark(false))
        assertEquals(AppSettings().theme, ThemeMode.System)                                // new installs follow the operating system
        // settings files written before "System" existed
        val file = AppStore.dir.resolve("settings.json")
        file.toFile().writeText("""{"dark": false, "fontSize": 14}""")
        assertEquals(ThemeMode.Light, AppStore.loadSettings().theme)
        file.toFile().writeText("""{"dark": true}""")
        assertEquals(ThemeMode.Dark, AppStore.loadSettings().theme)
        file.toFile().writeText("""{"theme": "system", "dark": true}""")                    // the new key wins
        assertEquals(ThemeMode.System, AppStore.loadSettings().theme)
        file.toFile().writeText("""{"theme": "bogus"}""")
        assertEquals(ThemeMode.System, AppStore.loadSettings().theme)
        AppStore.saveSettings(AppSettings(theme = ThemeMode.System))
        assertTrue(file.toFile().readText().contains("\"theme\": \"system\""))
    }

    @OptIn(androidx.compose.ui.InternalComposeUiApi::class)
    @Test fun appColoursFollowTheSystemThemeOnlyInSystemMode() = runDesktopComposeUiTest(1100, 760) {
        AppStore.saveState(AppStore.SavedState(emptyList(), emptyMap()))
        val state = AppState(CoroutineScope(Dispatchers.Default))
        val os = androidx.compose.runtime.mutableStateOf(androidx.compose.ui.SystemTheme.Dark)
        setContent { androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.LocalSystemTheme provides os.value) { OpenRestApp(state) } }
        fun bg(): Int { val img = onAllNodes(isRoot()).onFirst().captureToImage(); return img.asSkiaBitmap().getColor(900, 500) }
        fun luminance(c: Int) = ((c shr 16 and 255) * 299 + (c shr 8 and 255) * 587 + (c and 255) * 114) / 1000
        state.updateSettings(AppSettings(theme = ThemeMode.System)); waitForIdle()
        assertTrue(luminance(bg()) < 100, "system dark -> dark app")
        os.value = androidx.compose.ui.SystemTheme.Light; waitForIdle()
        assertTrue(luminance(bg()) > 150, "the OS switched to light -> the app follows")
        state.updateSettings(AppSettings(theme = ThemeMode.Dark)); waitForIdle()
        assertTrue(luminance(bg()) < 100, "an explicit Dark ignores the OS")
        os.value = androidx.compose.ui.SystemTheme.Dark
        state.updateSettings(AppSettings(theme = ThemeMode.Light)); waitForIdle()
        assertTrue(luminance(bg()) > 150, "an explicit Light ignores the OS")
    }
}
