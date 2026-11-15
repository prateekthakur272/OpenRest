package dev.prateekthakur.openrest

import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.storage.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.io.path.createTempDirectory
import kotlin.test.*

class SidebarPersistenceTest {
    @Test fun sidePaneSurvivesRestart() {
        AppStore.saveState(AppStore.SavedState(emptyList(), emptyMap()))
        val col = Collections.create(createTempDirectory("sp"), "C")
        val folder = Collections.createFolder(col, "Users")
        val first = AppState(CoroutineScope(Dispatchers.Default))
        first.openCollection(col)
        first.expanded.clear(); first.expanded[col.toString()] = true; first.expanded[folder.toString()] = true
        first.sidebarTab = "History"; first.sidebarWidth = 0.3f; first.sidebarVisible = false
        first.persist()

        val second = AppState(CoroutineScope(Dispatchers.Default))
        assertEquals("History", second.sidebarTab)
        assertEquals(0.3f, second.sidebarWidth)
        assertFalse(second.sidebarVisible)
        assertEquals(col, second.selectedCollection)
        assertEquals(true, second.expanded[folder.toString()])
    }

    @Test fun layoutSnapshotChangesWithTheSidePane() {
        val s = AppState(CoroutineScope(Dispatchers.Default))
        val before = s.layoutSnapshot()
        s.sidebarWidth = 0.4f
        assertNotEquals(before, s.layoutSnapshot())
    }
}
