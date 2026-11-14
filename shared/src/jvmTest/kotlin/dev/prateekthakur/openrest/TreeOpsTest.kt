package dev.prateekthakur.openrest

import dev.prateekthakur.openrest.app.AppState
import dev.prateekthakur.openrest.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.io.path.*
import kotlin.test.*

class TreeOpsTest {
    private fun setup(): Triple<AppState, java.nio.file.Path, java.nio.file.Path> {
        AppStore.saveState(AppStore.SavedState(emptyList(), emptyMap()))
        val dir = Collections.create(createTempDirectory("tree"), "T")
        val f = Collections.createFolder(dir, "Sub")
        listOf("A", "B", "C").forEach { Collections.createRequest(dir, ApiRequest(name = it, url = "http://x/$it")) }
        Collections.createRequest(f, ApiRequest(name = "Inner", url = "http://x/i"))
        val st = AppState(CoroutineScope(Dispatchers.Default))
        st.openCollection(dir)
        return Triple(st, dir, f)
    }

    private fun order(st: AppState, dir: java.nio.file.Path) = st.collection(dir)!!.children.filterIsInstance<RequestNode>().map { it.name }

    @Test fun reorder() {
        val (st, dir, _) = setup()
        assertEquals(listOf("A", "B", "C"), order(st, dir))
        val c = st.collection(dir)!!.allRequests().first { it.name == "C" }
        val a = st.collection(dir)!!.allRequests().first { it.name == "A" }
        st.dropRequest(dir, c, a, after = false)
        assertEquals(listOf("C", "A", "B"), order(st, dir))
        val b = st.collection(dir)!!.allRequests().first { it.name == "B" }
        val cc = st.collection(dir)!!.allRequests().first { it.name == "C" }
        st.dropRequest(dir, cc, b, after = true)
        assertEquals(listOf("A", "B", "C"), order(st, dir))
    }

    @Test fun moveIntoFolderAndOut() {
        val (st, dir, f) = setup()
        val a = st.collection(dir)!!.allRequests().first { it.name == "A" }
        st.moveNode(dir, a.path, f)
        assertEquals(listOf("B", "C"), order(st, dir))
        assertEquals(setOf("A", "Inner"), (st.collection(dir)!!.children.filterIsInstance<FolderNode>()[0].children.map { it.name }).toSet())
        // drop into another folder position via dropRequest (moves into target's folder)
        val inner = st.collection(dir)!!.allRequests().first { it.name == "Inner" }
        val b = st.collection(dir)!!.allRequests().first { it.name == "B" }
        st.dropRequest(dir, inner, b, after = true)
        assertEquals(listOf("B", "Inner", "C"), order(st, dir))
    }

    @Test fun inlineRenameKeepsTabs() {
        val (st, dir, _) = setup()
        val node = st.collection(dir)!!.allRequests().first { it.name == "B" }
        st.openRequest(dir, node)
        st.renameNode(dir, node, "Bee")
        assertEquals("Bee", st.activeTab!!.draft.name)
        assertTrue(st.activeTab!!.filePath.name.startsWith("Bee"))
        assertFalse(st.activeTab!!.dirty)
        assertNotNull(st.collection(dir)!!.allRequests().firstOrNull { it.name == "Bee" })
    }

    @Test fun cannotMoveFolderIntoItself() {
        val (st, dir, f) = setup()
        st.moveNode(dir, f, f)
        assertTrue(f.exists())
    }
}
