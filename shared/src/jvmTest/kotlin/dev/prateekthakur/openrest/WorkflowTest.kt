package dev.prateekthakur.openrest

import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.run.*
import dev.prateekthakur.openrest.core.storage.*
import dev.prateekthakur.openrest.utils.*

import kotlin.io.path.*
import kotlin.test.*

class WorkflowTest {
    private fun server(): com.sun.net.httpserver.HttpServer {
        val srv = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        srv.createContext("/") { ex ->
            val path = ex.requestURI.path
            val body = when {
                path == "/login" -> """{"token":"abc123","user":{"id":7},"roles":["a","b"]}"""
                else -> """{"path":"$path","auth":"${ex.requestHeaders.getFirst("X-Auth") ?: ""}","echo":"${ex.requestHeaders.getFirst("X-Echo") ?: ""}"}"""
            }.toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.responseHeaders.add("X-Trace", "t-1")
            ex.sendResponseHeaders(if (path == "/missing") 404 else 200, body.size.toLong()); ex.responseBody.write(body); ex.close()
        }
        srv.start()
        return srv
    }

    private fun echo(r: StepRun) = Json.obj(Json.parse(r.result!!.response!!.text))!!

    @Test fun inputsFlowBetweenSteps() {
        val srv = server(); val base = "http://127.0.0.1:${srv.address.port}"
        try {
            val dir = Collections.create(createTempDirectory("wf"), "W")
            Collections.createRequest(dir, ApiRequest(name = "Login", method = "POST", url = "$base/login"))
            Collections.createRequest(dir, ApiRequest(name = "Profile", url = "$base/users/{{uid}}", headers = listOf(KV("X-Echo", "{{st}}|{{trace}}|{{first_role}}"))))
            val col = Collections.load(dir)
            val login = col.allRequests().first { it.name == "Login" }
            val profile = col.allRequests().first { it.name == "Profile" }
            val wf = Workflow("Flow", listOf(
                WorkflowStep("s1", col.requestRef(login)),
                WorkflowStep("s2", col.requestRef(profile), listOf(
                    StepInput(InputTarget.Variable, "uid", "{{s1.body.user.id}}"),
                    StepInput(InputTarget.Variable, "st", "{{s1.status}}"),
                    StepInput(InputTarget.Variable, "trace", "{{s1.header.x-trace}}"),
                    StepInput(InputTarget.Variable, "first_role", "{{s1.body.roles[1]}}"),
                    StepInput(InputTarget.Header, "X-Auth", "Bearer {{s1.body.token}}"),
                )),
            ))
            WorkflowStore.save(dir, wf)
            val loaded = Collections.load(dir)
            assertEquals(wf, loaded.workflows.single())

            val runs = mutableListOf<StepRun>()
            WorkflowRunner.run(RunCtx(loaded, null, mutableMapOf(), AppSettings()), loaded.workflows[0], { false }, {}, { runs += it })
            assertEquals(2, runs.size)
            assertTrue(runs.none { it.failed }, runs.toString())
            val body = echo(runs[1])
            assertEquals("/users/7", body["path"])
            assertEquals("Bearer abc123", body["auth"])
            assertEquals("200|t-1|b", body["echo"])
        } finally { srv.stop(0) }
    }

    @Test fun everyTargetAndManyStepsIntoOneRequest() {
        val srv = server(); val base = "http://127.0.0.1:${srv.address.port}"
        try {
            val dir = Collections.create(createTempDirectory("wf"), "W")
            Collections.createRequest(dir, ApiRequest(name = "A", method = "POST", url = "$base/login"))
            Collections.createRequest(dir, ApiRequest(name = "B", url = "$base/other"))
            Collections.createRequest(dir, ApiRequest(name = "Target", method = "POST", url = "$base/items/:item?x=1", bodyMode = BodyMode.Json, bodyJson = """{"keep":true,"user":{"name":"n"}}"""))
            val col = Collections.load(dir)
            fun ref(n: String) = col.requestRef(col.allRequests().first { it.name == n })
            val wf = Workflow("Many", listOf(
                WorkflowStep("s1", ref("A")), WorkflowStep("s2", ref("B")),
                WorkflowStep("s3", ref("Target"), listOf(
                    StepInput(InputTarget.PathParam, "item", "{{s1.body.user.id}}"),
                    StepInput(InputTarget.PathAppend, "", "{{s2.status}}"),
                    StepInput(InputTarget.Query, "who", "{{s1.body.token}}-{{s2.status}}"),   // two steps, one value
                    StepInput(InputTarget.Bearer, "", "{{s1.body.token}}"),
                    StepInput(InputTarget.Body, "user.id", "{{s1.body.user.id}}"),
                    StepInput(InputTarget.Body, "list[1]", "x"),
                )),
            ))
            val runs = mutableListOf<StepRun>()
            WorkflowRunner.run(RunCtx(col, null, mutableMapOf(), AppSettings()), wf, { false }, {}, { runs += it })
            assertEquals(3, runs.size); assertTrue(runs.none { it.failed }, runs.toString())
            val res = runs[2].result!!
            assertEquals("POST", res.request!!.method)
            assertTrue(res.request!!.url.endsWith("/items/7/200?x=1&who=abc123-200"), res.request!!.url)
            assertTrue(res.request!!.headers.any { it.first == "Authorization" && it.second == "Bearer abc123" }, res.request!!.headers.toString())
            val sent = Json.obj(Json.parse((res.request!!.body as Body.Raw).text))!!
            assertEquals(true, sent["keep"])
            assertEquals("n", Json.obj(sent["user"])!!["name"])
            assertEquals(7L, (Json.obj(sent["user"])!!["id"] as Number).toLong())      // typed as a number, not "7"
            assertEquals(listOf(null, "x"), sent["list"])
            // the stored request is untouched
            assertEquals("""{"keep":true,"user":{"name":"n"}}""", Collections.load(dir).allRequests().first { it.name == "Target" }.request.bodyJson)
        } finally { srv.stop(0) }
    }

    @Test fun tokensWorkDirectlyInTheRequest() {
        val srv = server(); val base = "http://127.0.0.1:${srv.address.port}"
        try {
            val dir = Collections.create(createTempDirectory("wf"), "W")
            Collections.createRequest(dir, ApiRequest(name = "A", method = "POST", url = "$base/login"))
            Collections.createRequest(dir, ApiRequest(name = "B", url = "$base/users/{{s1.body.user.id}}", headers = listOf(KV("X-Auth", "t={{s1.body.token}}"))))
            val col = Collections.load(dir)
            fun ref(n: String) = col.requestRef(col.allRequests().first { it.name == n })
            val runs = mutableListOf<StepRun>()
            WorkflowRunner.run(RunCtx(col, null, mutableMapOf(), AppSettings()), Workflow("t", listOf(WorkflowStep("s1", ref("A")), WorkflowStep("s2", ref("B")))), { false }, {}, { runs += it })
            val body = echo(runs[1])
            assertEquals("/users/7", body["path"]); assertEquals("t=abc123", body["auth"])
        } finally { srv.stop(0) }
    }

    @Test fun oldInputFormatStillLoads() {
        val m = Yml.parse("""
            name: Old
            steps:
              - id: s1
                request: a.yaml
              - id: s2
                request: b.yaml
                inputs:
                  - name: tok
                    from: s1
                    source: field
                    path: data.token
        """.trimIndent()) as Map<*, *>
        val wf = WorkflowIO.fromMap(m, "x")
        assertEquals(StepInput(InputTarget.Variable, "tok", "{{s1.body.data.token}}"), wf.steps[1].inputs.single())
    }

    @Test fun fullResponseAndMissingValues() {
        val resp = ExecResult(response = HttpResult(status = 201, headers = listOf("A" to "b"), body = """{"k":[1,2]}""".toByteArray(), timeMs = 5))
        val full = Json.obj(Json.parse(WorkflowRunner.resolve("s1.response", resp).first))!!
        assertEquals(201L, (full["status"] as Number).toLong())
        assertEquals("""{"k":[1,2]}""", Json.stringify(full["body"]))
        val (v, warn) = WorkflowRunner.resolve("s1.body.nope.deep", resp)
        assertEquals("", v); assertNotNull(warn)
        assertNotNull(WorkflowRunner.resolve("s1.body.x", null).second)
        assertNotNull(WorkflowRunner.resolve("s1.wat", resp).second)
        assertEquals("""{"k":[1,2]}""", WorkflowRunner.resolve("s1.body", resp).first)
        assertTrue(WorkflowRunner.isStepRef("s12.body.x")); assertFalse(WorkflowRunner.isStepRef("baseUrl"))
    }

    @Test fun stopsOnFailureAndFlagsBadReferences() {
        val srv = server(); val base = "http://127.0.0.1:${srv.address.port}"
        try {
            val dir = Collections.create(createTempDirectory("wf"), "W")
            Collections.createRequest(dir, ApiRequest(name = "Bad", url = "http://127.0.0.1:1/x"))
            Collections.createRequest(dir, ApiRequest(name = "Ok", url = "$base/ok"))
            val col = Collections.load(dir)
            val bad = col.requestRef(col.allRequests().first { it.name == "Bad" }); val ok = col.requestRef(col.allRequests().first { it.name == "Ok" })
            val rc = { RunCtx(col, null, mutableMapOf(), AppSettings()) }
            var runs = mutableListOf<StepRun>()
            WorkflowRunner.run(rc(), Workflow("a", listOf(WorkflowStep("s1", bad), WorkflowStep("s2", ok))), { false }, {}, { runs += it })
            assertEquals(1, runs.size)                     // stopped after the failing step
            runs = mutableListOf()
            WorkflowRunner.run(rc(), Workflow("a", listOf(WorkflowStep("s1", bad), WorkflowStep("s2", ok)), stopOnFail = false), { false }, {}, { runs += it })
            assertEquals(2, runs.size)
            runs = mutableListOf()
            // forward reference (source step runs later) is rejected, disabled steps are skipped, missing requests reported
            WorkflowRunner.run(rc(), Workflow("a", listOf(
                WorkflowStep("s1", ok, listOf(StepInput(InputTarget.Variable, "v", "{{s2.status}}"))),
                WorkflowStep("s2", ok, enabled = false),
                WorkflowStep("s3", "gone.yaml"),
            ), stopOnFail = false), { false }, {}, { runs += it })
            assertEquals(2, runs.size)
            assertTrue(runs[0].notes.single().contains("not run yet"), runs[0].notes.toString())
            assertNull(runs[1].node); assertTrue(runs[1].failed)
        } finally { srv.stop(0) }
    }

    @Test fun renameAndDelete() {
        val dir = Collections.create(createTempDirectory("wf"), "W")
        WorkflowStore.save(dir, Workflow("One"))
        WorkflowStore.save(dir, Workflow("Two"))
        assertFailsWith<IllegalArgumentException> { WorkflowStore.save(dir, Workflow("Two"), "One") }
        WorkflowStore.save(dir, Workflow("Three"), "One")
        assertEquals(listOf("Three", "Two"), WorkflowStore.load(dir).map { it.name })
        WorkflowStore.delete(dir, "Two")
        assertEquals(listOf("Three"), WorkflowStore.load(dir).map { it.name })
        assertTrue(Collections.load(dir).children.isEmpty()) // workflows are not treated as requests
    }

    @Test fun slotsListWhatARequestCanBeFilledWith() {
        val req = ApiRequest(
            url = "http://h/orders/:orderId/items/:itemId?page=1", params = listOf(KV("sort", "asc")), pathParams = listOf(KV("itemId", "9")),
            headers = listOf(KV("X-Trace", "t"), KV("Off", "x", enabled = false)),
            bodyMode = BodyMode.Json, bodyJson = """{"user":{"id":1,"tags":["a","b"]},"note":"hi"}""",
            auth = Auth(AuthMode.Bearer, token = "tok"),
        )
        val slots = Slots.of(req).map { Triple(it.target, it.name, it.current) }
        assertTrue(Triple(InputTarget.PathParam, "orderId", "") in slots)
        assertTrue(Triple(InputTarget.PathParam, "itemId", "9") in slots)
        assertTrue(Triple(InputTarget.Query, "page", "1") in slots && Triple(InputTarget.Query, "sort", "asc") in slots)
        assertTrue(slots.any { it.first == InputTarget.Header && it.second == "X-Trace" } && slots.none { it.second == "Off" })
        assertTrue(Triple(InputTarget.Body, "user.id", "1") in slots && Triple(InputTarget.Body, "user.tags[1]", "b") in slots && Triple(InputTarget.Body, "note", "hi") in slots)
        assertTrue(Triple(InputTarget.Bearer, "", "tok") in slots)
    }

    @Test fun friendlyTextHidesIdsAndBraces() {
        val steps = listOf(WorkflowStep("s1", "a.yaml"), WorkflowStep("s2", "b.yaml"))
        val name = { st: WorkflowStep -> if (st.id == "s1") "Login" else "Create" }
        assertEquals("[Login › token]", Slots.friendly("{{s1.body.token}}", steps, name))
        assertEquals("Bearer [Login › token]-[Create › status code]", Slots.friendly("Bearer {{s1.body.token}}-{{s2.status}}", steps, name))
        assertEquals("[Login › header X-Id]", Slots.friendly("{{s1.header.X-Id}}", steps, name))
        assertEquals("plain {{baseUrl}}", Slots.friendly("plain {{baseUrl}}", steps, name))
    }
}
