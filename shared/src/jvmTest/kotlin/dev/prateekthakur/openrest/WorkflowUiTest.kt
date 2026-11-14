package dev.prateekthakur.openrest

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.*
import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.core.*
import dev.prateekthakur.openrest.ui.OpenRestApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File
import kotlin.io.path.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Builds a realistic workflow (list → count → if → loop → request per item), runs it through the app state and renders it. */
@OptIn(ExperimentalTestApi::class)
class WorkflowUiTest {
    private val out = File(System.getProperty("shots") ?: "/private/tmp/claude-501/-Users-ent-0554-IdeaProjects-OpenRest/e2e29356-c9b9-40cc-a6f8-baf7c71fe55c/scratchpad/shots").also { it.mkdirs() }

    private fun shot(t: DesktopComposeUiTest, name: String) {
        val img = t.onAllNodes(isRoot()).onFirst().captureToImage()
        File(out, "$name.png").writeBytes(org.jetbrains.skia.Image.makeFromBitmap(img.asSkiaBitmap()).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes)
    }

    @Test fun businessWorkflow() = runDesktopComposeUiTest(1360, 2300) {
        AppStore.saveState(AppStore.SavedState(emptyList(), emptyMap()))
        val srv = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        srv.createContext("/") { ex ->
            val body = when {
                ex.requestURI.path == "/orders" -> """{"orders":[{"id":11,"sku":"A-1","qty":2,"price":9.5},{"id":12,"sku":"B-7","qty":1,"price":20},{"id":13,"sku":"C-3","qty":5,"price":1.25}]}"""
                else -> """{"ok":true,"path":"${ex.requestURI.path}","by":"${ex.requestHeaders.getFirst("X-Source") ?: ""}"}"""
            }.toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json"); ex.sendResponseHeaders(200, body.size.toLong()); ex.responseBody.write(body); ex.close()
        }
        srv.start()
        try {
            val dir = Collections.create(createTempDirectory("wfui"), "Shop")
            Collections.saveEnvironment(dir, Environment("Local", listOf(KV("baseUrl", "http://127.0.0.1:${srv.address.port}"))))
            Collections.createRequest(dir, ApiRequest(name = "List orders", url = "{{baseUrl}}/orders"))
            Collections.createRequest(dir, ApiRequest(name = "Ship order", method = "POST", url = "{{baseUrl}}/orders/:orderId/ship?express=false", headers = listOf(KV("X-Source", "workflow")), bodyMode = BodyMode.Json, bodyJson = """{"sku":"","qty":1}"""))
            val state = AppState(CoroutineScope(Dispatchers.Default))
            state.openCollection(dir); state.setEnv(dir, "Local")
            setContent { OpenRestApp(state) }
            waitForIdle()

            // build it the way a user would, through the same state operations the UI calls
            WorkflowStore.save(dir, Workflow("Ship all open orders")); state.reload(dir)
            state.openWorkflow(dir, "Ship all open orders"); waitForIdle()
            val tab = state.activeTab!!; val ws = tab.workflow!!
            val c = state.collection(dir)!!
            state.addWorkflowStep(tab, c.allRequests().first { it.name == "List orders" })
            state.addWorkflowLogic(tab, StepKind.Set, setOp = SetOp.Count)
            state.addWorkflowLogic(tab, StepKind.Set, setOp = SetOp.Sum)
            val (list, count, total) = ws.draft.steps.map { it.id }
            ws.updateStep(count) { it.copy(list = "{{$list.body.orders}}") }
            ws.updateStep(total) { it.copy(list = "{{$list.body.orders}}", path = "price") }
            state.addWorkflowLogic(tab, StepKind.If)
            val iff = ws.draft.steps.last().id
            ws.updateStep(iff) { it.copy(conditions = listOf(Condition("{{count}}", CondOp.Gt, "0"), Condition("{{total}}", CondOp.Lt, "1000"))) }
            state.addWorkflowLogic(tab, StepKind.Loop, iff, 0, LoopMode.ForEach)
            val loop = ws.draft.steps.findStep(iff)!!.children.single().id
            ws.updateStep(loop) { it.copy(list = "{{$list.body.orders}}", title = "Ship each order", itemVar = "order") }
            state.addWorkflowStep(tab, c.allRequests().first { it.name == "Ship order" }, loop, 0)
            val ship = ws.draft.steps.findStep(loop)!!.children.single().id
            ws.updateStep(ship) { s -> s.copy(inputs = listOf(
                StepInput(InputTarget.PathParam, "orderId", "{order.id}"),
                StepInput(InputTarget.Query, "express", "true"),
                StepInput(InputTarget.Body, "sku", "{order.sku}"),
                StepInput(InputTarget.Body, "qty", "{order.qty}"),
                StepInput(InputTarget.Header, "X-Round", "{index}"),
            )) }
            state.addWorkflowLogic(tab, StepKind.Stop, iff, 1)
            ws.updateStep(ws.draft.steps.findStep(iff)!!.otherwise.single().id) { it.copy(stopFail = true, value = "Nothing to ship, or the total is too high") }
            waitForIdle(); shot(this, "wf-1-built")

            state.runWorkflow(tab)
            waitUntil(timeoutMillis = 20000) { !ws.running && ws.events.isNotEmpty() }
            waitForIdle()
            val log = ws.events.map { it.text }
            println(log.joinToString("\n"))
            assertTrue(log.any { it.contains("count = 3") }, log.toString())
            assertTrue(log.any { it.contains("total = 30.75") }, log.toString())
            assertTrue(log.any { it.contains("→ then") }, log.toString())
            assertEquals(3, ws.counts[ship])
            assertTrue(ws.events.all { it.ok }, log.toString())
            // the last round really did customise the request
            val sent = ws.runs[ship]!!.result!!.request!!
            assertTrue(sent.url.contains("/orders/13/ship") && sent.url.contains("express=true"), sent.url)
            assertEquals("""{"sku":"C-3","qty":5}""", Json.stringify(Json.parse((sent.body as Body.Raw).text)))
            assertEquals("2", sent.headers.first { it.first == "X-Round" }.second)
            // a run does not open the log; everything in it starts collapsed
            kotlin.test.assertFalse(ws.logExpanded)
            onNodeWithTag("log-0").assertDoesNotExist()
            onNodeWithText("running…").assertDoesNotExist()
            onNodeWithText("all ok").assertExists()                                           // ...but the bar already tells how it went
            onNodeWithTag("log-toggle").performClick(); waitForIdle()
            assertTrue(ws.logExpanded)
            onNodeWithTag("log-0").assertExists()                                             // top-level entries are listed...
            shot(this, "wf-2f-log-default")
            val ifEvent = ws.events.indexOfFirst { it.kind == StepKind.If }
            val loopEvent = ws.events.indexOfFirst { it.kind == StepKind.Loop }
            onNodeWithTag("log-$loopEvent").assertDoesNotExist()                              // ...what is inside the If is hidden
            onNodeWithTag("log-$ifEvent").performClick(); waitForIdle()
            onNodeWithTag("log-$loopEvent").assertExists()
            val firstRound = ws.events.indexOfFirst { it.stepId == ship && !it.sub }
            onNodeWithTag("log-$firstRound").assertDoesNotExist()                              // ...and the loop's rounds are hidden until the loop is opened
            onNodeWithTag("log-$loopEvent").performClick(); waitForIdle()
            onNodeWithTag("log-$firstRound").assertExists()
            // a step that ran in a loop keeps every round's response
            ws.selected = ship; waitForIdle()
            onAllNodesWithText("View response").assertCountEquals(4)                         // only the run log has it: one per request that ran (1 + 3 rounds)
            onAllNodesWithText("View response").onLast().performClick(); waitForIdle()       // the last round of Ship order
            onNode(hasText("Round 3 of 3")).assertExists()
            assertEquals(3, ws.history[ship]!!.size)
            assertEquals(listOf("/orders/11/ship", "/orders/12/ship", "/orders/13/ship"), ws.history[ship]!!.map { Json.obj(Json.parse(it.result!!.response!!.text))!!["path"] })
            shot(this, "wf-2c-rounds")
            state.modal = null; waitForIdle()
            // the docked log folds to its bar, unfolds again, and its top edge resizes it
            onNodeWithTag("log-toggle").performClick(); waitForIdle()
            kotlin.test.assertFalse(ws.logExpanded)
            onNodeWithTag("log-0").assertDoesNotExist()
            onNodeWithText("RUN LOG").assertExists()
            onNodeWithTag("log-toggle").performClick(); waitForIdle()
            assertTrue(ws.logExpanded)
            onNodeWithTag("log-$firstRound").assertExists()                                   // what was opened is still open
            val before = ws.logHeight
            onNodeWithTag("log-resize").performMouseInput { moveTo(center); press(); moveBy(androidx.compose.ui.geometry.Offset(0f, -120f)); release() }; waitForIdle()
            assertTrue(ws.logHeight > before + 80, "dragging the top edge up grows the panel: $before -> ${ws.logHeight}")
            onNodeWithTag("log-resize").performMouseInput { moveTo(center); press(); moveBy(androidx.compose.ui.geometry.Offset(0f, 400f)); release() }; waitForIdle()
            assertTrue(ws.logHeight >= 120f, "never smaller than the minimum")
            ws.logHeight = 300f; waitForIdle()
            // the run log: summary, rows that expand, and an "only problems" filter
            ws.selected = null; waitForIdle()
            onNodeWithText("RUN LOG").assertExists()
            onNodeWithText("all ok").assertExists()
            val shipEvent = ws.events.indexOfFirst { it.stepId == ship && !it.sub }
            // clicking a log entry expands that entry in the log — it must not open the config pane
            onNodeWithTag("log-$shipEvent").performClick(); waitForIdle()
            assertEquals(null, ws.selected)
            assertEquals(true, ws.logOpen[shipEvent])
            onNodeWithTag("log-details-$shipEvent", useUnmergedTree = true).assertExists()
            onNodeWithText("SENT", useUnmergedTree = true).assertExists()
            onNodeWithText("VALUES FILLED IN", useUnmergedTree = true).assertExists()
            onAllNodesWithText("RESPONSE", substring = true, useUnmergedTree = true).assertCountEquals(0)      // the log never shows response bodies
            onNodeWithText("\"path\": \"/orders/11/ship\"", substring = true, useUnmergedTree = true).assertDoesNotExist()
            shot(this, "wf-2e-log-expanded")
            onNodeWithTag("log-$shipEvent").performClick(); waitForIdle()                       // click again to collapse
            onNodeWithTag("log-details-$shipEvent", useUnmergedTree = true).assertDoesNotExist()
            assertEquals(null, ws.selected)
            // an explicit link inside the expanded entry is the way to open the step
            onNodeWithTag("log-$shipEvent").performClick(); waitForIdle()
            onNodeWithText("Configure this step", useUnmergedTree = true).performScrollTo().performClick(); waitForIdle()
            assertEquals(ship, ws.selected)
            ws.selected = null; ws.logOpen.clear(); waitForIdle()
            ws.events.add(RunEvent(ship, 1, StepKind.Request, "Ship order · connection refused", ok = false, at = 99, label = "Ship order"))
            waitForIdle()
            onAllNodesWithText("1 problem").onLast().assertExists()                               // the log header (the top bar shows one too)
            onNodeWithText("Only problems").performClick(); waitForIdle()
            assertTrue(ws.logProblemsOnly)
            shot(this, "wf-2d-runlog-problems")
            onNodeWithText("Only problems").performClick(); waitForIdle()
            ws.events.removeAt(ws.events.lastIndex)
            ws.selected = ship; waitForIdle()
            // the side pane shows only while a block is selected, and the X closes it
            ws.selected = null; waitForIdle()
            onNodeWithTag("close-config").assertDoesNotExist()
            ws.selected = ship; waitForIdle()
            onNodeWithTag("close-config").assertExists()
            shot(this, "wf-2-run")
            onNodeWithTag("close-config").performClick(); waitForIdle()
            onNodeWithTag("close-config").assertDoesNotExist()
            assertEquals(null, ws.selected)
            shot(this, "wf-2b-closed")
        } finally { srv.stop(0) }
    }

    /** Method, URL, headers, query and body of a step are edited right in the step, then really sent that way. */
    @Test fun editEveryPartOfAStepInPlace() = runDesktopComposeUiTest(1360, 1500) {
        AppStore.saveState(AppStore.SavedState(emptyList(), emptyMap()))
        val srv = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        srv.createContext("/") { ex ->
            val body = """{"id":42,"name":"Ada","tags":["x","y"]}""".toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json"); ex.sendResponseHeaders(200, body.size.toLong()); ex.responseBody.write(body); ex.close()
        }
        srv.start()
        try {
            val base = "http://127.0.0.1:${srv.address.port}"
            val dir = Collections.create(createTempDirectory("wfedit"), "Edit")
            Collections.createRequest(dir, ApiRequest(name = "Create user", method = "POST", url = "$base/users"))
            Collections.createRequest(dir, ApiRequest(
                name = "Update user", method = "POST", url = "$base/users/:id?notify=false", headers = listOf(KV("X-Source", "original")),
                bodyMode = BodyMode.Json, bodyJson = """{"name":"old","meta":{"by":"me"}}""", pathParams = listOf(KV("id", "1")),
            ))
            val state = AppState(CoroutineScope(Dispatchers.Default))
            state.openCollection(dir)
            setContent { OpenRestApp(state) }
            waitForIdle()
            WorkflowStore.save(dir, Workflow("Edit")); state.reload(dir)
            state.openWorkflow(dir, "Edit"); waitForIdle()
            val tab = state.activeTab!!; val ws = tab.workflow!!
            val c = state.collection(dir)!!
            state.addWorkflowStep(tab, c.allRequests().first { it.name == "Create user" })
            state.addWorkflowStep(tab, c.allRequests().first { it.name == "Update user" })
            waitForIdle(); shot(this, "wf-3-step-editor")
            val update = ws.draft.steps[1].id

            // the request's real values are in the step, not just placeholders
            onNode(hasSetTextAction() and hasText("$base/users/:id?notify=false")).assertExists()
            // 1. method
            onAllNodes(hasText("POST") and hasClickAction()).onLast().performClick(); waitForIdle()
            onNodeWithText("PUT").performClick(); waitForIdle()
            assertEquals(StepInput(InputTarget.Method, "", "PUT"), ws.draft.steps[1].inputs.single())
            // 2. url
            onNode(hasSetTextAction() and hasText("$base/users/:id?notify=false")).performTextReplacement("$base/v2/users/:id?notify=false"); waitForIdle()
            assertTrue(ws.draft.steps[1].inputs.any { it.target == InputTarget.Url && it.value.endsWith("/v2/users/:id?notify=false") })
            // 3. path param, then link it to the first step's output
            onNode(hasSetTextAction() and hasText("1")).performTextReplacement("7"); waitForIdle()
            assertTrue(ws.draft.steps[1].inputs.any { it.target == InputTarget.PathParam && it.name == "id" && it.value == "7" })
            // 4. query
            onNode(hasSetTextAction() and hasText("false")).performTextReplacement("true"); waitForIdle()
            // 5. headers
            onAllNodesWithText("Headers").onLast().performClick(); waitForIdle()
            onNode(hasSetTextAction() and hasText("original")).performTextReplacement("edited"); waitForIdle()
            assertTrue(ws.draft.steps[1].inputs.any { it.target == InputTarget.Header && it.name == "X-Source" && it.value == "edited" })
            shot(this, "wf-4-headers")
            // 6. a body field, filled from step 1's output
            onAllNodesWithText("Body").onLast().performClick(); waitForIdle()
            state.runWorkflow(tab, ws.draft.steps[0].id); waitUntil(timeoutMillis = 15000) { !ws.running }; waitForIdle()
            ws.updateStep(update) { it.copy(inputs = it.inputs + StepInput(InputTarget.Body, "meta.by", "{{${ws.draft.steps[0].id}.body.name}}") + StepInput(InputTarget.Body, "name", "{{${ws.draft.steps[0].id}.body.id}}")) }
            waitForIdle(); shot(this, "wf-5-body")

            state.runWorkflow(tab); waitUntil(timeoutMillis = 15000) { !ws.running && ws.runs.size == 2 }; waitForIdle()
            val sent = ws.runs[update]!!.result!!.request!!
            assertEquals("PUT", sent.method)
            assertEquals("$base/v2/users/7?notify=true", sent.url)
            assertEquals("edited", sent.headers.first { it.first == "X-Source" }.second)
            assertEquals("""{"name":42,"meta":{"by":"Ada"}}""", Json.stringify(Json.parse((sent.body as Body.Raw).text)))
            shot(this, "wf-6-after-run")
        } finally { srv.stop(0) }
    }

    /** Configure what to pass on at the producing step, then pick it by name in the next one — the whole flow through the UI. */
    @Test fun passOnAtTheProducerThenPickByName() = runDesktopComposeUiTest(1360, 1500) {
        AppStore.saveState(AppStore.SavedState(emptyList(), emptyMap()))
        val srv = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        srv.createContext("/") { ex ->
            val body = (if (ex.requestURI.path == "/login") """{"data":{"token":"tok-9","user":{"id":42}}}""" else """{"path":"${ex.requestURI.path}","auth":"${ex.requestHeaders.getFirst("Authorization") ?: ""}"}""").toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json"); ex.sendResponseHeaders(200, body.size.toLong()); ex.responseBody.write(body); ex.close()
        }
        srv.start()
        try {
            val base = "http://127.0.0.1:${srv.address.port}"
            val dir = Collections.create(createTempDirectory("wfpass"), "Pass")
            Collections.createRequest(dir, ApiRequest(name = "Login", method = "POST", url = "$base/login"))
            Collections.createRequest(dir, ApiRequest(name = "Profile", url = "$base/users/:id", headers = listOf(KV("Authorization", "none"))))
            val state = AppState(CoroutineScope(Dispatchers.Default))
            state.openCollection(dir)
            setContent { OpenRestApp(state) }
            waitForIdle()
            WorkflowStore.save(dir, Workflow("Pass")); state.reload(dir)
            state.openWorkflow(dir, "Pass"); waitForIdle()
            val tab = state.activeTab!!; val ws = tab.workflow!!
            val c = state.collection(dir)!!
            state.addWorkflowStep(tab, c.allRequests().first { it.name == "Login" })
            state.addWorkflowStep(tab, c.allRequests().first { it.name == "Profile" })
            val (login, profile) = ws.draft.steps.map { it.id }

            // the run log is docked at the bottom from the start, folded to just its bar until something runs
            onNodeWithText("RUN LOG").assertExists()
            onNodeWithText("run the workflow to see every step here").assertExists()
            kotlin.test.assertFalse(ws.logExpanded)
            // run the producer once, look at its response, and choose what to pass on
            state.runWorkflow(tab, login); waitUntil(timeoutMillis = 15000) { !ws.running && ws.runs.isNotEmpty() }; waitForIdle()
            kotlin.test.assertFalse(ws.logExpanded)                                               // a run does not open the dock by itself
            onNodeWithTag("log-toggle").performClick(); waitForIdle()                             // the user opens it
            ws.selected = login; waitForIdle()   // open the producing step in the side pane
            // the side pane only configures; the complete response opens from "View response" in the run log
            onNodeWithText("Response").assertDoesNotExist()
            onAllNodesWithText("View response").onFirst().performClick(); waitForIdle()
            onNode(hasText("\"token\": \"tok-9\"", substring = true)).assertExists()             // pretty, complete body
            shot(this, "wf-7a-response-body")
            onAllNodesWithText("Headers").onLast().performClick(); waitForIdle()
            onAllNodes(hasText("content-type")).onLast().assertExists()
            onAllNodesWithText("Sent").onLast().performClick(); waitForIdle()
            onAllNodes(hasText("$base/login")).onLast().assertExists()                               // the request as it was sent
            shot(this, "wf-7b-response-sent")
            onAllNodesWithText("Fields").onLast().performClick(); waitForIdle()
            shot(this, "wf-7-response-fields")
            onAllNodesWithText("Pass on").filter(hasClickAction()).let { all ->
                // rows of the dialog's Fields tab: Full body, body.data.token, body.data.user.id, Status code, …
                all[1].performClick(); waitForIdle()
                onAllNodesWithText("Pass on").filter(hasClickAction())[1].performClick(); waitForIdle()
            }
            assertEquals(listOf("token" to "data.token", "id" to "data.user.id"), ws.draft.steps[0].outputs.map { it.name to it.path })
            state.modal = null; waitForIdle()

            // the pane shows the request and, below a divider, what the step passes on — no tabs to switch
            onNodeWithText("Add output").assertExists()
            onNodeWithText("PASS ON TO LATER STEPS").assertExists()
            onAllNodesWithText("Params").onFirst().assertExists()
            shot(this, "wf-8-passed-on")

            ws.selected = profile; waitForIdle()   // now configure the next step
            // typing { in any field of the next step suggests what the earlier steps pass on
            onNode(hasSetTextAction() and hasText("$base/users/:id")).performTextReplacement("$base/users/:id?src={to"); waitForIdle()
            onNodeWithTag("suggestion-menu").assertExists()
            shot(this, "wf-11-autocomplete")
            onNode(hasSetTextAction() and isFocused()).performKeyInput { pressKey(androidx.compose.ui.input.key.Key.Enter) }; waitForIdle()
            assertTrue(ws.draft.steps[1].inputs.any { it.target == InputTarget.Url && it.value.endsWith("?src={token}") }, ws.draft.steps[1].inputs.toString())

            // next step: pick them by name
            onAllNodes(hasText("Use value") and hasClickAction()).filter(hasText("Use value")).let { all -> all[1].performClick() }  // [0] URL, [1] :id path param
            waitForIdle(); shot(this, "wf-9-pick-by-name")
            onAllNodesWithText("42").onLast().performClick(); waitForIdle()
            assertEquals("{id}", ws.draft.steps[1].inputs.first { it.target == InputTarget.PathParam }.value)

            state.runWorkflow(tab); waitUntil(timeoutMillis = 15000) { !ws.running && ws.runs.size == 2 }; waitForIdle()
            val sent = ws.runs[profile]!!.result!!.request!!
            assertEquals("$base/users/42?src=tok-9", sent.url)
            assertTrue(ws.events.any { it.text.contains("passes on") && it.text.contains("token = tok-9") && it.text.contains("id = 42") }, ws.events.map { it.text }.toString())
            // the log shows what was passed on as a table: name | value
            val passOnIdx = ws.events.indexOfFirst { it.sub }
            assertEquals(listOf("token" to "tok-9", "id" to "42"), ws.events[passOnIdx].fields)
            onNodeWithTag("log-passon-$passOnIdx", useUnmergedTree = true).assertDoesNotExist()   // collapsed by default...
            onAllNodesWithText("→ token, id", useUnmergedTree = true).onFirst().assertExists()      // ...with a hint of what was passed on
            onNodeWithTag("log-0").performClick(); waitForIdle()                                    // open the Login entry
            onNodeWithTag("log-passon-$passOnIdx", useUnmergedTree = true).assertExists()
            onNodeWithText("PASSED ON", useUnmergedTree = true).assertExists()
            onAllNodesWithText("tok-9", useUnmergedTree = true).onFirst().assertExists()
            // each passed-on field has one copy action: it copies the value
            onNodeWithTag("passon-copy-value-token", useUnmergedTree = true).performClick(); waitForIdle()
            assertEquals("Copied the value of token", state.toast)
            onNodeWithTag("passon-copy-value-id", useUnmergedTree = true).performClick(); waitForIdle()
            assertEquals("Copied the value of id", state.toast)
            onNodeWithTag("passon-copy-ref-id", useUnmergedTree = true).assertDoesNotExist()
            shot(this, "wf-10b-passon-copy")
            // where each value comes from is shown: under the field, on the step's row, and in its log entry
            onAllNodesWithText("from Login", useUnmergedTree = true).onFirst().assertExists()
            onAllNodesWithText("{token}", useUnmergedTree = true).onFirst().assertExists()
            onAllNodesWithText("← token, id", useUnmergedTree = true).onFirst().assertExists()
            val profileEntry = ws.events.indexOfFirst { it.stepId == profile && !it.sub }
            onNodeWithTag("log-$profileEntry").performClick(); waitForIdle()
            onNodeWithText("USED FROM EARLIER STEPS", useUnmergedTree = true).assertExists()
            shot(this, "wf-10c-used-info")
            shot(this, "wf-10-flow")
        } finally { srv.stop(0) }
    }

    /** A realistic response: a big JSON array (like jsonplaceholder /posts), in a smaller window, opened from the outline and from the log. */
    @Test fun viewResponseOpensForALargeResponse() = runDesktopComposeUiTest(1280, 720) {
        AppStore.saveState(AppStore.SavedState(emptyList(), emptyMap()))
        val big = (1..100).joinToString(",", "[", "]") { """{"userId":${it % 10},"id":$it,"title":"title number $it of the posts list","body":"quia et suscipit suscipit recusandae consequuntur expedita et cum reprehenderit molestiae ut ut quas totam nostrum rerum est autem sunt rem eveniet architecto"}""" }
        val srv = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        srv.createContext("/") { ex ->
            val body = big.toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json; charset=utf-8"); ex.sendResponseHeaders(200, body.size.toLong()); ex.responseBody.write(body); ex.close()
        }
        srv.start()
        try {
            val dir = Collections.create(createTempDirectory("wfbig"), "JSON Placeholder")
            Collections.createRequest(dir, ApiRequest(name = "Posts", url = "http://127.0.0.1:${srv.address.port}/posts/"))
            val state = AppState(CoroutineScope(Dispatchers.Default))
            state.openCollection(dir)
            setContent { OpenRestApp(state) }
            waitForIdle()
            WorkflowStore.save(dir, Workflow("Create post")); state.reload(dir)
            state.openWorkflow(dir, "Create post"); waitForIdle()
            val tab = state.activeTab!!; val ws = tab.workflow!!
            state.addWorkflowStep(tab, state.collection(dir)!!.allRequests().first())
            state.runWorkflow(tab); waitUntil(timeoutMillis = 15000) { !ws.running && ws.history.isNotEmpty() }; waitForIdle()

            // the outline rows have no button; the run log has it (open the dock first — it stays folded after a run)
            onNodeWithTag("log-toggle").performClick(); waitForIdle()
            onAllNodesWithText("View response").assertCountEquals(1)
            // like a real mouse: move over it, press, release — with the step's side pane open
            ws.selected = ws.draft.steps.first().id; waitForIdle()
            onNodeWithText("View response").performMouseInput { moveTo(center); press(); advanceEventTime(80); release() }; waitForIdle()
            assertTrue(state.modal is Modal.ViewResponse, "modal after a real mouse click: ${state.modal}")
            // the app reloads its collections when the window regains focus; the open dialog must survive that
            state.reloadAll(); waitForIdle()
            assertTrue(state.modal is Modal.ViewResponse, "modal after a reload: ${state.modal}")
            onNodeWithText("Response — Posts").assertExists()
            shot(this, "wf-big-response")
            state.modal = null; waitForIdle()
            onNodeWithText("View response").performClick(); waitForIdle()
            assertTrue(state.modal is Modal.ViewResponse, "modal after a plain click: ${state.modal}")
            state.modal = null; waitForIdle()
        } finally { srv.stop(0) }
    }

    /** The dialog must open for every kind of outcome: errors, HTML, empty bodies, plain text, binary. */
    @Test fun viewResponseOpensForEveryKindOfOutcome() = runDesktopComposeUiTest(1360, 860) {
        AppStore.saveState(AppStore.SavedState(emptyList(), emptyMap()))
        val srv = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        fun reply(ex: com.sun.net.httpserver.HttpExchange, code: Int, type: String?, body: ByteArray) {
            if (type != null) ex.responseHeaders.add("Content-Type", type)
            if (body.isEmpty() && code == 204) ex.sendResponseHeaders(code, -1) else { ex.sendResponseHeaders(code, body.size.toLong()); ex.responseBody.write(body) }
            ex.close()
        }
        srv.createContext("/") { ex ->
            when (ex.requestURI.path) {
                "/html404" -> reply(ex, 404, "text/html", "<html><body><h1>Not Found</h1></body></html>".toByteArray())
                "/empty" -> reply(ex, 204, null, ByteArray(0))
                "/text" -> reply(ex, 200, "text/plain", "plain text ünïcode ✓".toByteArray())
                "/bin" -> reply(ex, 200, "application/octet-stream", ByteArray(5000) { (it * 7).toByte() })
                "/badjson" -> reply(ex, 200, "application/json", """{"a": [1, 2,""".toByteArray())
                "/notype" -> reply(ex, 200, null, """{"a":1}""".toByteArray())
                else -> reply(ex, 500, "application/json", """{"error":"boom"}""".toByteArray())
            }
        }
        srv.start()
        try {
            val base = "http://127.0.0.1:${srv.address.port}"
            val dir = Collections.create(createTempDirectory("wfkinds"), "Kinds")
            val cases = listOf("refused" to "http://127.0.0.1:1/x", "html404" to "$base/html404", "empty" to "$base/empty", "text" to "$base/text", "bin" to "$base/bin", "badjson" to "$base/badjson", "notype" to "$base/notype", "server500" to "$base/boom")
            cases.forEach { (n, u) -> Collections.createRequest(dir, ApiRequest(name = n, url = u)) }
            val state = AppState(CoroutineScope(Dispatchers.Default))
            state.openCollection(dir)
            setContent { OpenRestApp(state) }
            waitForIdle()
            WorkflowStore.save(dir, Workflow("Kinds", stopOnFail = false)); state.reload(dir)
            state.openWorkflow(dir, "Kinds"); waitForIdle()
            val tab = state.activeTab!!; val ws = tab.workflow!!
            state.collection(dir)!!.allRequests().sortedBy { r -> cases.indexOfFirst { it.first == r.name } }.forEach { state.addWorkflowStep(tab, it) }
            state.runWorkflow(tab); waitUntil(timeoutMillis = 20000) { !ws.running && ws.history.size == cases.size }; waitForIdle()
            ws.draft.steps.forEachIndexed { i, st ->
                val name = cases[i].first
                state.modal = Modal.ViewResponse(tab.id, st.id); waitForIdle()
                assertTrue(state.modal is Modal.ViewResponse, "dialog for $name closed itself")
                onNodeWithText("Response — $name").assertExists()
                for (sub in listOf("Body", "Fields", "Headers", "Sent", "Tests")) { onAllNodesWithText(sub).onLast().performClick(); waitForIdle() }   // every sub-tab renders
                shot(this, "wf-kind-$name")
                state.modal = null; waitForIdle()
            }
        } finally { srv.stop(0) }
    }
}
