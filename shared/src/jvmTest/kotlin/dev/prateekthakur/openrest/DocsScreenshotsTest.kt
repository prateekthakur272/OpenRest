package dev.prateekthakur.openrest

import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.run.*
import dev.prateekthakur.openrest.core.storage.*
import dev.prateekthakur.openrest.features.shell.*

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File
import kotlin.io.path.*
import kotlin.test.Test

/**
 * Regenerates the screenshots used by README.md and docs/. It does nothing in a normal test run; to refresh the images:
 *
 *     DOCS_SHOTS=$PWD/docs/screenshots ./gradlew :shared:jvmTest --tests '*DocsScreenshotsTest*' --rerun
 */
@OptIn(ExperimentalTestApi::class)
class DocsScreenshotsTest {
    private val out: File? = System.getenv("DOCS_SHOTS")?.let { File(it).also { d -> d.mkdirs() } }

    private fun shot(t: DesktopComposeUiTest, name: String) {
        val img = t.onAllNodes(isRoot()).onFirst().captureToImage()
        File(out!!, "$name.png").writeBytes(org.jetbrains.skia.Image.makeFromBitmap(img.asSkiaBitmap()).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes)
    }

    private val pets = """[{"id":1,"name":"Biscuit","species":"dog","status":"available","price":120.0},{"id":2,"name":"Mochi","species":"cat","status":"available","price":80.5},{"id":3,"name":"Pepper","species":"parrot","status":"pending","price":45.0}]"""

    @Test fun docsScreenshots() = runDesktopComposeUiTest(1360, 860) {
        if (out == null) return@runDesktopComposeUiTest
        AppStore.saveState(AppStore.SavedState(emptyList(), emptyMap()))
        val srv = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        srv.createContext("/") { ex ->
            val path = ex.requestURI.path
            val body = when {
                path == "/pets" -> pets
                path.startsWith("/pets/") && path.endsWith("/adopt") -> """{"ok":true,"adopted":"${path.split("/")[2]}","by":"${ex.requestHeaders.getFirst("X-Source") ?: ""}"}"""
                path.startsWith("/pets/") -> """{"id":${path.substringAfterLast('/')},"name":"Biscuit","species":"dog","status":"available","price":120.0,"tags":["friendly","vaccinated"],"owner":null}"""
                else -> """{"ok":true}"""
            }.toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json"); ex.sendResponseHeaders(200, body.size.toLong()); ex.responseBody.write(body); ex.close()
        }
        srv.start()
        try {
            val dir = Collections.create(createTempDirectory("docs"), "Pet Store API")
            Collections.saveEnvironment(dir, Environment("Local", listOf(KV("baseUrl", "http://127.0.0.1:${srv.address.port}"), KV("token", "demo-token")), secrets = setOf("token")))
            Collections.saveEnvironment(dir, Environment("Staging", listOf(KV("baseUrl", "https://staging.petstore.example"), KV("token", "")), secrets = setOf("token")))
            val pets = Collections.createFolder(dir, "Pets")
            val orders = Collections.createFolder(dir, "Orders")
            Collections.createRequest(pets, ApiRequest(name = "List pets", url = "{{baseUrl}}/pets", assertions = listOf(KV("res.status", "eq 200"))))
            Collections.createRequest(pets, ApiRequest(
                name = "Get pet", url = "{{baseUrl}}/pets/:id?verbose=true", params = listOf(KV("verbose", "true")), pathParams = listOf(KV("id", "1")),
                headers = listOf(KV("Accept", "application/json")), assertions = listOf(KV("res.status", "eq 200")),
                tests = "test('has a name', () => {\n  expect(res.body.name).to.equal('Biscuit');\n});\n\ntest('is available', () => {\n  expect(res.body.status).to.equal('available');\n});",
            ))
            Collections.createRequest(pets, ApiRequest(name = "Add pet", method = "POST", url = "{{baseUrl}}/pets", bodyMode = BodyMode.Json, bodyJson = "{\n  \"name\": \"Biscuit\",\n  \"species\": \"dog\",\n  \"price\": 120\n}"))
            Collections.createRequest(pets, ApiRequest(name = "Adopt pet", method = "POST", url = "{{baseUrl}}/pets/:petId/adopt", pathParams = listOf(KV("petId", "1")), headers = listOf(KV("X-Source", "docs")), bodyMode = BodyMode.Json, bodyJson = """{"adopter":"ada"}"""))
            Collections.createRequest(orders, ApiRequest(name = "Place order", method = "POST", url = "{{baseUrl}}/orders", bodyMode = BodyMode.Json, bodyJson = """{"petId":1,"qty":1}"""))
            val state = AppState(CoroutineScope(Dispatchers.Default))
            state.updateSettings(AppSettings(theme = ThemeMode.Dark))
            state.openCollection(dir); state.setEnv(dir, "Local")
            setContent { OpenRestApp(state) }; waitForIdle()

            // welcome (nothing open yet)
            state.expanded[pets.toString()] = true; state.expanded[orders.toString()] = true; waitForIdle()
            shot(this, "welcome")

            // a request and its response
            val col = { state.collection(dir)!! }
            state.openRequest(dir, col().allRequests().first { it.name == "Get pet" }); waitForIdle()
            state.send(state.activeTab!!)
            waitUntil(timeoutMillis = 15000) { state.activeTab?.result != null }; waitForIdle()
            shot(this, "request-response")
            state.activeTab!!.resTab = "Tests"; waitForIdle(); shot(this, "response-tests")
            state.activeTab!!.resTab = "Response"

            // light theme and side-by-side layout
            state.updateSettings(state.settings.copy(theme = ThemeMode.Light, verticalLayout = false)); waitForIdle()
            shot(this, "light-side-by-side")
            state.updateSettings(state.settings.copy(theme = ThemeMode.Dark, verticalLayout = true)); waitForIdle()

            // request body
            state.openRequest(dir, col().allRequests().first { it.name == "Add pet" }); state.activeTab!!.reqTab = "Body"; waitForIdle()
            shot(this, "request-body")

            // dialogs
            state.modal = Modal.Palette; waitForIdle(); shot(this, "command-palette")
            state.modal = Modal.Envs(dir); waitForIdle(); shot(this, "environments")
            state.modal = Modal.Codegen(state.activeTab!!.id); waitForIdle(); shot(this, "code-generation")
            state.modal = Modal.Import; waitForIdle(); shot(this, "import")
            state.prefsSection = "Appearance"; state.modal = Modal.Settings; waitForIdle(); shot(this, "preferences")
            state.modal = Modal.Onboarding; waitForIdle(); shot(this, "onboarding")
            state.modal = null; waitForIdle()

            // collection runner
            state.openRunner(dir, null); waitForIdle()
            state.startRunner(state.activeTab!!)
            waitUntil(timeoutMillis = 20000) { state.activeTab?.runner?.running == false && state.activeTab?.runner?.results?.isNotEmpty() == true }; waitForIdle()
            Thread.sleep(400); waitForIdle(); shot(this, "runner")
        } finally { srv.stop(0) }
    }

    @Test fun workflowScreenshots() = runDesktopComposeUiTest(1360, 900) {
        if (out == null) return@runDesktopComposeUiTest
        AppStore.saveState(AppStore.SavedState(emptyList(), emptyMap()))
        val srv = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        srv.createContext("/") { ex ->
            val path = ex.requestURI.path
            val body = (if (path == "/pets") pets else """{"ok":true,"path":"$path"}""").toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json"); ex.sendResponseHeaders(200, body.size.toLong()); ex.responseBody.write(body); ex.close()
        }
        srv.start()
        try {
            val dir = Collections.create(createTempDirectory("docswf"), "Pet Store API")
            Collections.saveEnvironment(dir, Environment("Local", listOf(KV("baseUrl", "http://127.0.0.1:${srv.address.port}"))))
            Collections.createRequest(dir, ApiRequest(name = "List pets", url = "{{baseUrl}}/pets"))
            Collections.createRequest(dir, ApiRequest(name = "Adopt pet", method = "POST", url = "{{baseUrl}}/pets/:petId/adopt", headers = listOf(KV("X-Source", "workflow")), bodyMode = BodyMode.Json, bodyJson = """{"adopter":""}"""))
            val state = AppState(CoroutineScope(Dispatchers.Default))
            state.updateSettings(AppSettings(theme = ThemeMode.Dark))
            state.openCollection(dir); state.setEnv(dir, "Local")
            setContent { OpenRestApp(state) }; waitForIdle()
            WorkflowStore.save(dir, Workflow("Adopt every available pet")); state.reload(dir)
            state.openWorkflow(dir, "Adopt every available pet"); waitForIdle()
            val tab = state.activeTab!!; val ws = tab.workflow!!; val c = state.collection(dir)!!
            state.addWorkflowStep(tab, c.allRequests().first { it.name == "List pets" })
            state.addWorkflowLogic(tab, StepKind.Set, setOp = SetOp.Count)
            val (list, count) = ws.draft.steps.map { it.id }
            ws.updateStep(count) { it.copy(list = "{{$list.body}}") }
            state.addWorkflowLogic(tab, StepKind.If)
            val iff = ws.draft.steps.last().id
            ws.updateStep(iff) { it.copy(conditions = listOf(Condition("{{count}}", CondOp.Gt, "0"))) }
            state.addWorkflowLogic(tab, StepKind.Loop, iff, 0, LoopMode.ForEach)
            val loop = ws.draft.steps.findStep(iff)!!.children.single().id
            ws.updateStep(loop) { it.copy(list = "{{$list.body}}", title = "Adopt each pet", itemVar = "pet") }
            state.addWorkflowStep(tab, c.allRequests().first { it.name == "Adopt pet" }, loop, 0)
            val adopt = ws.draft.steps.findStep(loop)!!.children.single().id
            ws.updateStep(adopt) { s -> s.copy(inputs = listOf(StepInput(InputTarget.PathParam, "petId", "{pet.id}"), StepInput(InputTarget.Body, "adopter", "{pet.name}"))) }
            state.addWorkflowLogic(tab, StepKind.Stop, iff, 1)
            ws.updateStep(ws.draft.steps.findStep(iff)!!.otherwise.single().id) { it.copy(stopFail = true, value = "There are no pets to adopt") }
            ws.selected = adopt; waitForIdle(); shot(this, "workflow-builder")
            state.runWorkflow(tab)
            waitUntil(timeoutMillis = 20000) { !ws.running && ws.events.isNotEmpty() }; waitForIdle()
            ws.logExpanded = true; waitForIdle(); shot(this, "workflow-run-log")
        } finally { srv.stop(0) }
    }
}
