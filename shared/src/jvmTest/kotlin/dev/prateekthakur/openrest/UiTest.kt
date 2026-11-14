package dev.prateekthakur.openrest

import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.run.*
import dev.prateekthakur.openrest.core.storage.*
import dev.prateekthakur.openrest.features.shell.*

import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asSkiaBitmap
import dev.prateekthakur.openrest.features.shell.OpenRestApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File
import kotlin.io.path.*
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class UiTest {
    private val out = File(System.getProperty("shots") ?: "/private/tmp/claude-501/-Users-ent-0554-IdeaProjects-OpenRest/e2e29356-c9b9-40cc-a6f8-baf7c71fe55c/scratchpad/shots").also { it.mkdirs() }

    private fun shot(t: DesktopComposeUiTest, name: String) {
        val img = t.onAllNodes(isRoot()).onFirst().captureToImage()
        val data = org.jetbrains.skia.Image.makeFromBitmap(img.asSkiaBitmap()).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!
        File(out, "$name.png").writeBytes(data.bytes)
    }

    @Test fun render() = runDesktopComposeUiTest(1360, 860) {
        AppStore.saveState(AppStore.SavedState(emptyList(), emptyMap()))
        val counter = java.util.concurrent.atomic.AtomicInteger()
        val srv = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        srv.createContext("/") { ex ->
            val b = "{\"id\":7,\"name\":\"Ada\",\"tags\":[\"a\",\"b\"],\"ok\":true,\"score\":${counter.incrementAndGet()},\"nested\":{\"x\":null}}".toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json"); ex.sendResponseHeaders(200, b.size.toLong()); ex.responseBody.write(b); ex.close()
        }
        srv.start()
        try {
            val dir = Collections.create(createTempDirectory("orcol"), "Demo API")
            Collections.saveEnvironment(dir, Environment("Local", listOf(KV("baseUrl", "http://127.0.0.1:${srv.address.port}"))))
            val f = Collections.createFolder(dir, "Users")
            Collections.createRequest(f, ApiRequest(
                name = "Get user", method = "GET", url = "{{baseUrl}}/users/:id?verbose=true", params = listOf(KV("verbose", "true")),
                pathParams = listOf(KV("id", "7")), headers = listOf(KV("Accept", "application/json")),
                assertions = listOf(KV("res.status", "eq 200")), tests = "test('has name', () => {\n  expect(res.body.name).to.equal('Ada');\n});",
            ))
            Collections.createRequest(dir, ApiRequest(name = "Create user", method = "POST", url = "{{baseUrl}}/users", bodyMode = BodyMode.Json, bodyJson = "{\n  \"name\": \"Ada\",\n  \"age\": 36\n}"))
            val state = AppState(CoroutineScope(Dispatchers.Default))
            state.openCollection(dir)
            state.setEnv(dir, "Local")
            setContent { OpenRestApp(state) }
            waitForIdle()
            shot(this, "1-welcome")
            state.expanded[f.toString()] = true
            waitForIdle()
            state.collection(dir)!!.allRequests().first { it.name == "Get user" }.let { state.openRequest(dir, it) }
            waitForIdle()
            shot(this, "2-request")
            state.send(state.activeTab!!)
            waitUntil(timeoutMillis = 15000) { state.activeTab?.result != null }
            waitForIdle()
            shot(this, "3-response")
            // second send -> response history + diff + find
            state.send(state.activeTab!!); waitUntil(timeoutMillis = 15000) { state.activeTab!!.responses.size == 2 && !state.activeTab!!.sending }
            state.activeTab!!.diff = true; waitForIdle(); shot(this, "3b-diff")
            state.activeTab!!.diff = false; state.activeTab!!.find = "Ada"; waitForIdle(); shot(this, "3c-find")
            state.activeTab!!.find = ""
            state.activeTab!!.resTab = "Headers"; waitForIdle(); shot(this, "3d-resheaders")
            state.activeTab!!.reqTab = "Body"; state.activeTab!!.resTab = "Tests"
            waitForIdle(); shot(this, "4-tests")
            state.openRequest(dir, state.collection(dir)!!.allRequests().first { it.name == "Create user" })
            state.activeTab!!.reqTab = "Body"
            state.update(state.activeTab!!) { it.copy(bodyJson = "{\n  \"name\": \"Ada\",\n  \"age\": \n}") }
            waitForIdle(); shot(this, "5-body")
            onNode(hasText("JSON") and hasClickAction()).performClick(); waitForIdle(); shot(this, "5b-body-menu")
            onNodeWithText("Multipart Form").performClick(); waitForIdle()
            kotlin.test.assertEquals(BodyMode.Multipart, state.activeTab!!.draft.bodyMode)
            state.update(state.activeTab!!) { it.copy(bodyMode = BodyMode.Json) }; waitForIdle()
            state.update(state.activeTab!!) { it.copy(bodyJson = "{\n  \"name\": \"Ada\",\n  \"age\": 36\n}") }
            state.modal = Modal.Codegen(state.activeTab!!.id); waitForIdle(); shot(this, "6-codegen")
            onNode(hasText("cURL") and hasClickAction()).performClick(); waitForIdle()
            onNodeWithText("Python (requests)").performClick(); waitForIdle(); shot(this, "6b-codegen-python")
            onNode(hasText("Python (requests)") and hasClickAction()).performClick(); waitForIdle()
            onNodeWithText("Kotlin (OkHttp)").performClick(); waitForIdle(); shot(this, "6c-codegen-kotlin")
            state.modal = Modal.Envs(dir); waitForIdle(); shot(this, "7-envs")
            state.modal = Modal.Import; waitForIdle(); shot(this, "8-import")
            state.modal = Modal.Export(dir); waitForIdle(); shot(this, "8b-export")
            val prevTab = state.activeTabId
            state.modal = null; state.openRunner(dir, null); waitForIdle(); shot(this, "9-runner")
            state.closeTab(state.activeTabId!!, true); state.activeTabId = prevTab; waitForIdle()
            state.modal = null
            run {
                val orderFile = Collections.createRequest(dir, ApiRequest(name = "Get order", url = "{{baseUrl}}/orders/:orderId"))
                state.reload(dir)
                val c0 = state.collection(dir)!!
                val create = c0.allRequests().first { it.name == "Create user" }; val get = c0.allRequests().first { it.name == "Get user" }
                WorkflowStore.save(dir, Workflow("Onboard user", listOf(
                    WorkflowStep("s1", c0.requestRef(create)),
                    WorkflowStep("s2", c0.requestRef(c0.allRequests().first { it.name == "Get order" }), listOf(StepInput(InputTarget.Header, "X-Request-Id", "req-{{s1.body.data.id}}-{{s1.status}}"))),
                )))
                state.reload(dir); waitForIdle()
                shot(this, "9b-sidebar-workflow")
                state.openWorkflow(dir, "Onboard user"); waitForIdle(); shot(this, "9c-workflow")
                val ws = state.activeTab!!.workflow!!
                val ok = ExecResult(response = HttpResult(status = 201, headers = listOf("Content-Type" to "application/json"), body = """{"data":{"id":42,"name":"Ada"}}""".toByteArray(), timeMs = 83))
                ws.runs["s1"] = StepRun(ws.draft.steps[0], c0.requestByRef(ws.draft.steps[0].request), ok)
                ws.runs["s2"] = StepRun(ws.draft.steps[1], c0.requestByRef(ws.draft.steps[1].request), ok.copy(response = ok.response!!.copy(status = 200, timeMs = 41)), listOf("Header X-Request-Id" to "req-42-201"))
                ws.expanded = "s1"; waitForIdle(); shot(this, "9d-workflow-run")
                ws.expanded = null; waitForIdle()
                ws.selected = ws.draft.steps[1].id; waitForIdle()   // open the second step in the side pane
                shot(this, "9d1-workflow-needs")
                onAllNodesWithText("Use value")[1].performClick(); waitForIdle()   // [0] is the URL, [1] the "needs a value" row
                shot(this, "9d2-workflow-picker")
                // (clicking a field in the picker is covered by WorkflowUiTest in a taller window)
                ws.updateStep(ws.draft.steps[1].id) { it.copy(inputs = it.inputs + StepInput(InputTarget.PathParam, "orderId", "{{s1.body.data.id}}")) }; waitForIdle()
                kotlin.test.assertEquals(StepInput(InputTarget.PathParam, "orderId", "{{s1.body.data.id}}"), ws.draft.steps[1].inputs.last())
                waitForIdle(); shot(this, "9d3-workflow-linked")
                // logic blocks: count a list, branch on it, loop over it
                val tabNow = state.activeTab!!
                state.addWorkflowLogic(tabNow, StepKind.Set, setOp = SetOp.Count)
                val cnt = ws.draft.steps.last().id
                ws.updateStep(cnt) { it.copy(list = "{{s1.body.data.items}}") }
                state.addWorkflowLogic(tabNow, StepKind.If)
                val iff = ws.draft.steps.last().id
                ws.updateStep(iff) { it.copy(conditions = listOf(Condition("{{count}}", CondOp.Gt, "0"))) }
                state.addWorkflowLogic(tabNow, StepKind.Loop, iff, 0, LoopMode.ForEach)
                val lp = ws.draft.steps.findStep(iff)!!.children.last().id
                ws.updateStep(lp) { it.copy(list = "{{s1.body.data.items}}", title = "Order each item") }
                state.addWorkflowStep(tabNow, c0.allRequests().first { it.name == "Create user" }, lp, 0)
                state.addWorkflowLogic(tabNow, StepKind.Stop, iff, 1)
                ws.events.addAll(listOf(
                    RunEvent("s1", 0, StepKind.Request, "Create user · 201 · 83 ms"),
                    RunEvent(cnt, 0, StepKind.Set, "count = 3"),
                    RunEvent(iff, 0, StepKind.If, "If: 3 is greater than 0 → then"),
                    RunEvent(lp, 1, StepKind.Loop, "Order each item: for each of 3 items"),
                    RunEvent("s9", 2, StepKind.Request, "Create user · 201 · 40 ms"),
                    RunEvent("s9", 2, StepKind.Request, "Create user · 500 · 31 ms", ok = false),
                ))
                waitForIdle(); shot(this, "9d4-workflow-logic")
                state.modal = Modal.AddStep(state.activeTabId!!); waitForIdle(); shot(this, "9e-add-step")
                state.modal = null
                state.closeTab(state.activeTabId!!, true); state.activeTabId = prevTab; waitForIdle()
                Collections.delete(orderFile); WorkflowStore.delete(dir, "Onboard user"); state.reload(dir); waitForIdle()
            }
            state.modal = Modal.Palette; waitForIdle()
            onNode(hasSetTextAction() and hasText("Search requests, run", substring = true)).performTextInput("get"); waitForIdle(); shot(this, "11-palette")
            state.modal = null; state.sidebarTab = "History"; waitForIdle(); shot(this, "12-history")
            state.sidebarTab = "Collections"
            state.activeTab!!.reqTab = "Headers"; waitForIdle()
            state.update(state.activeTab!!) { it.copy(headers = emptyList()) }; waitForIdle()
            onNode(hasSetTextAction() and hasText("Header")).performTextInput("Con"); waitForIdle()
            onNodeWithText("Content-Type").assertExists()
            // arrow keys move the highlighted suggestion and Enter inserts it
            onNode(hasSetTextAction() and isFocused()).performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionDown) }; waitForIdle()
            shot(this, "13a-suggest-down")
            onNode(hasSetTextAction() and isFocused()).performKeyInput { pressKey(androidx.compose.ui.input.key.Key.Enter) }; waitForIdle()
            kotlin.test.assertEquals("Connection", state.activeTab!!.draft.headers[0].name)
            shot(this, "13-bulk")
            state.activeTab!!.reqTab = "Auth"; waitForIdle(); shot(this, "13b-auth")
            // pick an auth type from the menu and fill the form
            onNode(hasText("Inherit") and hasClickAction()).performClick(); waitForIdle()
            onNodeWithText("OAuth 2.0").performClick(); waitForIdle()
            kotlin.test.assertEquals(AuthMode.OAuth2, state.activeTab!!.draft.auth.mode)
            state.splitFraction = 0.8f; waitForIdle(); shot(this, "13e-auth-oauth2"); state.splitFraction = 0.45f; waitForIdle()
            onNode(hasText("Inherit") .not() and hasText("OAuth 2.0") and hasClickAction()).performClick(); waitForIdle()
            onNodeWithText("Bearer Token").performClick(); waitForIdle()
            kotlin.test.assertEquals(AuthMode.Bearer, state.activeTab!!.draft.auth.mode)
            onNode(hasSetTextAction() and hasText("{{token}}")).performTextInput("abc"); waitForIdle()
            kotlin.test.assertEquals("abc", state.activeTab!!.draft.auth.token)
            shot(this, "13f-auth-bearer")
            state.update(state.activeTab!!) { it.copy(auth = Auth(AuthMode.Inherit)) }; waitForIdle()
            state.activeTab!!.reqTab = "Params"; waitForIdle()
            // typing into the always-ready empty row creates a param and keeps focus
            onNode(hasSetTextAction() and hasText("Name")).performTextInput("pa"); waitForIdle()
            kotlin.test.assertEquals(1, state.activeTab!!.draft.params.size)
            onNode(hasSetTextAction() and isFocused()).assertExists()
            onNode(hasSetTextAction() and isFocused()).performTextInput("ge"); waitForIdle()
            kotlin.test.assertEquals("page", state.activeTab!!.draft.params[0].name)
            shot(this, "13c-params")
            state.update(state.activeTab!!) { it.copy(params = emptyList(), url = "{{baseUrl}}/users") }; waitForIdle()
            onNode(hasSetTextAction() and hasText("baseUrl", substring = true)).performTextReplacement("{{ba"); waitForIdle(); onNodeWithText("insert", substring = true).assertExists(); onNodeWithText("baseUrl").assertExists()
            val menu = onNodeWithTag("suggestion-menu").fetchSemanticsNode()
            println("MENU " + menu.size)
            kotlin.test.assertTrue(menu.size.width in 300..460 && menu.size.height < 400, "menu must be compact: " + menu.size)
            shot(this, "14-autocomplete")
            // url-encoded form: inline Decode chip
            state.update(state.activeTab!!) { it.copy(bodyMode = BodyMode.Form, formUrlEncoded = listOf(KV("q", "a%20b%26c"), KV("plain", "x"))) }
            state.activeTab!!.reqTab = "Body"; waitForIdle(); shot(this, "13d-decode")
            onAllNodesWithText("Decode").assertCountEquals(1)
            onNodeWithText("Decode").performClick(); waitForIdle()
            kotlin.test.assertEquals("a b&c", state.activeTab!!.draft.formUrlEncoded[0].value)
            onAllNodesWithText("Decode").assertCountEquals(0)
            state.updateSettings(state.settings.copy(theme = ThemeMode.Light)); waitForIdle(); shot(this, "10-light")
            // keyboard navigation + inline rename in the sidebar
            state.modal = null; state.sidebarTab = "Collections"; state.cursor = null; waitForIdle()
            onAllNodesWithText("Create user").onFirst().performClick(); waitForIdle()
            kotlin.test.assertNotNull(state.cursor)
            val before = state.cursor
            onAllNodes(isRoot()).onFirst().performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionDown) }; waitForIdle()
            kotlin.test.assertTrue(state.cursor != before)
            state.renaming = state.collection(dir)!!.allRequests().first { it.name == "Create user" }.path; waitForIdle()
            shot(this, "15-inline-rename")
            state.renaming = null
            // session restore
            state.persist()
            val restored = AppState(CoroutineScope(Dispatchers.Default))
            kotlin.test.assertEquals(state.tabs.size, restored.tabs.size)
            kotlin.test.assertEquals(state.activeTab!!.filePath, restored.activeTab!!.filePath)
        } finally { srv.stop(0) }
    }
}
