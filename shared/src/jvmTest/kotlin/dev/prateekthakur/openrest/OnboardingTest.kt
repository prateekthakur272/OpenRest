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
class OnboardingTest {
    private fun shot(t: DesktopComposeUiTest, name: String) {
        val img = t.onAllNodes(isRoot()).onFirst().captureToImage()
        File(System.getProperty("shots") ?: "build/shots").also { it.mkdirs() }.resolve("$name.png").writeBytes(org.jetbrains.skia.Image.makeFromBitmap(img.asSkiaBitmap()).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes)
    }
    private fun fresh() { File(AppStore.dir.toString(), "settings.json").delete(); File(AppStore.dir.toString(), "state.json").delete() }

    @Test fun firstRunIsDetectedAndClearedOnceSettingsAreSaved() {
        fresh(); assertTrue(AppStore.isFirstRun())
        AppStore.saveSettings(AppSettings()); assertFalse(AppStore.isFirstRun())
    }

    @Test fun walkthroughStepsThroughAndRemembersChoices() = runDesktopComposeUiTest(1360, 860) {
        fresh()
        val state = AppState(CoroutineScope(Dispatchers.Default))
        state.updateSettings(AppSettings()); fresh()
        state.modal = Modal.Onboarding
        setContent { OpenRestApp(state) }; waitForIdle()
        shot(this, "onboarding-1-welcome")
        onNodeWithTag("onb-back").assertDoesNotExist()
        onNodeWithTag("onb-next").performClick(); waitForIdle()
        shot(this, "onboarding-2-look")
        onNodeWithTag("onb-theme-Light").performClick(); waitForIdle()
        assertEquals(ThemeMode.Light, state.settings.theme)
        onNodeWithTag("onb-layout-side").performClick(); waitForIdle()
        assertFalse(state.settings.verticalLayout)
        onNodeWithTag("onb-next").performClick(); waitForIdle(); shot(this, "onboarding-3-tips")
        onNodeWithTag("onb-back").performClick(); waitForIdle(); onNodeWithTag("onb-theme-Dark").assertExists()   // back goes back
        onNodeWithTag("onb-next").performClick(); onNodeWithTag("onb-next").performClick(); waitForIdle()
        shot(this, "onboarding-4-start")
        onNodeWithTag("onb-skip").assertDoesNotExist()                               // nothing to skip on the last page
        onNodeWithTag("onb-import").performClick(); waitForIdle()
        assertEquals(Modal.Import, state.modal)                                       // the choice opens the next dialog...
        assertFalse(AppStore.isFirstRun())                                            // ...and the walkthrough is not shown again
        assertEquals(ThemeMode.Light, AppStore.loadSettings().theme)                  // choices were saved
    }

    @Test fun skipClosesAndCountsAsSeen() = runDesktopComposeUiTest(1360, 860) {
        val state = AppState(CoroutineScope(Dispatchers.Default)); state.updateSettings(AppSettings()); fresh()
        state.modal = Modal.Onboarding
        setContent { OpenRestApp(state) }; waitForIdle()
        onNodeWithTag("onb-skip").performClick(); waitForIdle()
        assertEquals(null, state.modal); assertFalse(AppStore.isFirstRun())
    }
}
