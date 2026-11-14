package dev.prateekthakur.openrest

import dev.prateekthakur.openrest.core.*
import kotlin.io.path.*
import dev.prateekthakur.openrest.core.Collection
import kotlin.test.*

class WorkflowLogicTest {
    private fun server(): com.sun.net.httpserver.HttpServer {
        val srv = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        srv.createContext("/") { ex ->
            val path = ex.requestURI.path
            val body = when (path) {
                "/list" -> """{"items":[{"id":1,"price":10.5,"name":"a"},{"id":2,"price":5,"name":"b"},{"id":3,"price":7,"name":"c"}],"empty":[]}"""
                else -> """{"path":"$path","q":"${ex.requestURI.query ?: ""}"}"""
            }.toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(200, body.size.toLong()); ex.responseBody.write(body); ex.close()
        }
        srv.start()
        return srv
    }

    private class Fixture(val col: Collection, val base: String, val srv: com.sun.net.httpserver.HttpServer) {
        fun ref(n: String) = col.requestRef(col.allRequests().first { it.name == n })
        fun run(wf: Workflow, stopAfter: String? = null): Pair<List<StepRun>, List<RunEvent>> {
            val runs = mutableListOf<StepRun>(); val events = mutableListOf<RunEvent>()
            WorkflowRunner.run(RunCtx(col, null, mutableMapOf(), AppSettings()), wf, { false }, {}, { runs += it }, { events += it }, stopAfter)
            return runs to events
        }
    }

    private fun fixture(block: Fixture.() -> Unit) {
        val srv = server(); val base = "http://127.0.0.1:${srv.address.port}"
        try {
            val dir = Collections.create(createTempDirectory("wfl"), "W")
            Collections.createRequest(dir, ApiRequest(name = "List", url = "$base/list"))
            Collections.createRequest(dir, ApiRequest(name = "Item", url = "$base/item/:id"))
            Fixture(Collections.load(dir), base, srv).block()
        } finally { srv.stop(0) }
    }

    private fun step(id: String, ref: String, vararg inputs: StepInput) = WorkflowStep(id, ref, inputs.toList())
    private fun texts(e: List<RunEvent>) = e.map { it.text }

    @Test fun forEachCountSumAndIf() = fixture {
        val wf = Workflow("w", listOf(
            step("s1", ref("List")),
            WorkflowStep("s2", kind = StepKind.Set, setOp = SetOp.Count, varName = "count", list = "{{s1.body.items}}"),
            WorkflowStep("s3", kind = StepKind.Set, setOp = SetOp.Sum, varName = "total", list = "{{s1.body.items}}", path = "price"),
            WorkflowStep("s4", kind = StepKind.Loop, loop = LoopMode.ForEach, list = "{{s1.body.items}}", children = listOf(
                step("s5", ref("Item"), StepInput(InputTarget.PathParam, "id", "{{item.id}}"), StepInput(InputTarget.Query, "n", "{{index}}-{{item.name}}")),
            )),
            WorkflowStep("s6", kind = StepKind.If, conditions = listOf(Condition("{{count}}", CondOp.Gt, "2")),
                children = listOf(WorkflowStep("s7", kind = StepKind.Set, varName = "size", value = "many")),
                otherwise = listOf(WorkflowStep("s8", kind = StepKind.Set, varName = "size", value = "few"))),
        ))
        val (runs, events) = run(wf)
        val items = runs.filter { it.step.id == "s5" }
        assertEquals(3, items.size)
        assertEquals(listOf(1, 2, 3), items.map { it.iteration })
        assertEquals(listOf("/item/1", "/item/2", "/item/3"), items.map { Json.obj(Json.parse(it.result!!.response!!.text))!!["path"] })
        assertEquals(listOf("n=0-a", "n=1-b", "n=2-c"), items.map { Json.obj(Json.parse(it.result!!.response!!.text))!!["q"] })
        val t = texts(events)
        assertTrue(t.any { it.contains("count = 3") }, t.toString())
        assertTrue(t.any { it.contains("total = 22.5") }, t.toString())
        assertTrue(t.any { it.contains("→ then") }, t.toString())
        assertTrue(t.any { it.contains("size = many") } && t.none { it.contains("size = few") }, t.toString())
    }

    @Test fun ifElseTakesTheOtherBranchAndAnyAll() = fixture {
        val wf = Workflow("w", listOf(
            step("s1", ref("List")),
            WorkflowStep("s2", kind = StepKind.If,
                conditions = listOf(Condition("{{s1.status}}", CondOp.Eq, "500"), Condition("{{s1.body.items[0].name}}", CondOp.Contains, "A")), matchAll = false,
                children = listOf(WorkflowStep("s3", kind = StepKind.Set, varName = "r", value = "any-hit")),
                otherwise = listOf(WorkflowStep("s4", kind = StepKind.Set, varName = "r", value = "miss"))),
            WorkflowStep("s5", kind = StepKind.If,
                conditions = listOf(Condition("{{s1.status}}", CondOp.Eq, "200"), Condition("{{s1.body.empty}}", CondOp.NotEmpty)), matchAll = true,
                children = listOf(WorkflowStep("s6", kind = StepKind.Set, varName = "r2", value = "yes")),
                otherwise = listOf(WorkflowStep("s7", kind = StepKind.Set, varName = "r2", value = "no"))),
        ))
        val t = texts(run(wf).second)
        assertTrue(t.any { it.contains("r = any-hit") }, t.toString())      // 'A' matches 'a' case-insensitively
        assertTrue(t.any { it.contains("r2 = no") }, t.toString())          // empty list is "empty"
    }

    @Test fun repeatWhileBreakAndStop() = fixture {
        val repeat = Workflow("w", listOf(WorkflowStep("s1", kind = StepKind.Loop, loop = LoopMode.Repeat, count = "2", children = listOf(step("s2", ref("Item"), StepInput(InputTarget.PathParam, "id", "{{index}}"))))))
        assertEquals(listOf(0, 1), run(repeat).first.map { it.iteration - 1 })

        val whileLoop = Workflow("w", listOf(WorkflowStep("s1", kind = StepKind.Loop, loop = LoopMode.While, conditions = listOf(Condition("{{index}}", CondOp.Lt, "3")),
            children = listOf(step("s2", ref("Item"), StepInput(InputTarget.PathParam, "id", "x"))))))
        assertEquals(3, run(whileLoop).first.size)

        val capped = Workflow("w", listOf(WorkflowStep("s1", kind = StepKind.Loop, loop = LoopMode.While, maxIterations = 4, conditions = listOf(Condition("1", CondOp.Eq, "1")),
            children = listOf(step("s2", ref("Item"), StepInput(InputTarget.PathParam, "id", "x"))))))
        val (cr, ce) = run(capped)
        assertEquals(4, cr.size); assertTrue(texts(ce).any { it.contains("limit") })

        // break out of a loop on the second round, then keep going after the loop
        val brk = Workflow("w", listOf(
            step("s0", ref("List")),
            WorkflowStep("s1", kind = StepKind.Loop, list = "{{s0.body.items}}", children = listOf(
                step("s2", ref("Item"), StepInput(InputTarget.PathParam, "id", "{{item.id}}")),
                WorkflowStep("s3", kind = StepKind.If, conditions = listOf(Condition("{{item.id}}", CondOp.Eq, "2")), children = listOf(WorkflowStep("s4", kind = StepKind.Stop, stopScope = StopScope.Loop))),
            )),
            step("s5", ref("Item"), StepInput(InputTarget.PathParam, "id", "after")),
        ))
        val (br, _) = run(brk)
        assertEquals(listOf("s0", "s2", "s2", "s5"), br.map { it.step.id })

        // stopping the workflow ends everything, and marks failure in the log
        val stop = Workflow("w", listOf(step("s1", ref("List")), WorkflowStep("s2", kind = StepKind.Stop, stopFail = true, value = "no stock"), step("s3", ref("List"))))
        val (sr, se) = run(stop)
        assertEquals(1, sr.size); assertTrue(se.last().text.contains("no stock") && !se.last().ok)
    }

    @Test fun notAListAndMissingReferencesAreReportedNotCrashing() = fixture {
        val wf = Workflow("w", listOf(
            step("s1", ref("List")),
            WorkflowStep("s2", kind = StepKind.Loop, list = "{{s1.body.items[0].name}}", children = listOf(step("s3", ref("Item"), StepInput(InputTarget.PathParam, "id", "1")))),
            WorkflowStep("s4", kind = StepKind.Set, setOp = SetOp.Count, varName = "n", list = "{{s9.body.x}}"),
            WorkflowStep("s5", kind = StepKind.Loop, list = "{{s1.body.empty}}", children = listOf(step("s6", ref("Item"), StepInput(InputTarget.PathParam, "id", "1")))),
        ))
        val (runs, events) = run(wf)
        assertEquals(1, runs.size)                       // nothing from the loops
        val t = texts(events)
        assertTrue(t.any { it.contains("is not a list") }, t.toString())
        assertTrue(t.any { it.contains("n = 0") }, t.toString())
        assertTrue(t.any { it.contains("for each of 0 items") }, t.toString())
    }

    @Test fun customisingEveryPartOfARequest() = fixture {
        val col2 = col
        Collections.createRequest(col2.path, ApiRequest(name = "Echo", method = "POST", url = "$base/echo/:kind?keep=1", bodyMode = BodyMode.Json, bodyJson = """{"a":1}""", headers = listOf(KV("X-A", "1"))))
        val c = Collections.load(col2.path)
        val fx = Fixture(c, base, srv)
        fun go(vararg i: StepInput) = fx.run(Workflow("w", listOf(step("s1", fx.ref("Echo"), *i)))).first.single().result!!.request!!
        assertEquals("PUT", go(StepInput(InputTarget.Method, "", "put")).method)
        assertTrue(go(StepInput(InputTarget.Url, "", "$base/other")).url.endsWith("/other"))
        assertTrue(go(StepInput(InputTarget.PathParam, "kind", "k1")).url.contains("/echo/k1?keep=1"))
        assertTrue(go(StepInput(InputTarget.Query, "page", "2")).url.endsWith("keep=1&page=2"))
        assertEquals("9", go(StepInput(InputTarget.Header, "x-a", "9")).headers.first { it.first.equals("X-A", true) }.second)
        assertEquals("""{"z":true}""", Json.stringify(Json.parse((go(StepInput(InputTarget.BodyRaw, "", """{"z": true}""")).body as Body.Raw).text)))
        assertEquals(2L, (Json.obj(Json.parse((go(StepInput(InputTarget.Body, "a", "2")).body as Body.Raw).text))!!["a"] as Number).toLong())
        // blank values never override the request
        assertEquals("GET".let { "POST" }, go(StepInput(InputTarget.Method, "", "")).method)
    }

    @Test fun runToHereStopsAfterThatBlock() = fixture {
        val wf = Workflow("w", listOf(step("s1", ref("List")), step("s2", ref("List")), step("s3", ref("List"))))
        assertEquals(listOf("s1", "s2"), run(wf, stopAfter = "s2").first.map { it.step.id })
    }

    @Test fun treeHelpersAndFileRoundTrip() {
        val wf = Workflow("tree", listOf(
            WorkflowStep("s1", "a.yaml", listOf(StepInput(InputTarget.Header, "X", "{{s1.status}}")), continueOnFail = true, title = "First"),
            WorkflowStep("s2", kind = StepKind.If, conditions = listOf(Condition("{{s1.status}}", CondOp.Gte, "200"), Condition("a", CondOp.Empty)), matchAll = false,
                children = listOf(WorkflowStep("s3", "b.yaml"), WorkflowStep("s4", kind = StepKind.Loop, loop = LoopMode.Repeat, count = "3", maxIterations = 9, itemVar = "x", indexVar = "i",
                    children = listOf(WorkflowStep("s5", kind = StepKind.Wait, value = "10")))),
                otherwise = listOf(WorkflowStep("s6", kind = StepKind.Set, setOp = SetOp.Sum, varName = "t", list = "{{x}}", path = "p"), WorkflowStep("s7", kind = StepKind.Stop, stopScope = StopScope.Loop, stopFail = true, value = "bad", enabled = false))),
        ), stopOnFail = false, description = "d")
        val dir = createTempDirectory("wfio")
        WorkflowStore.save(dir, wf)
        assertEquals(wf, WorkflowStore.load(dir).single())

        assertEquals(listOf("s1", "s2", "s3", "s4", "s5", "s6", "s7"), wf.steps.walk().map { it.id })
        assertEquals("s8", newStepId(wf.steps))
        assertEquals(listOf("s1", "s3"), wf.steps.requestsBefore("s4").map { it.id })
        assertEquals(setOf("i"), wf.steps.variablesBefore("s5").toSet())               // a Repeat loop only has its index
        assertEquals(listOf("i"), wf.steps.variablesBefore("s6"))
        assertEquals(listOf("s1", "s2"), wf.steps.removeStep("s3").map { it.id }.take(2))
        assertNull(wf.steps.removeStep("s5").findStep("s5"))
        val added = wf.steps.addStep("s2", 1, WorkflowStep("s9", kind = StepKind.Wait))
        assertEquals(listOf("s6", "s7", "s9"), added.findStep("s2")!!.otherwise.map { it.id })
        assertEquals(listOf("s7", "s6"), wf.steps.moveStep("s7", -1).findStep("s2")!!.otherwise.map { it.id })
        assertEquals(wf.steps.map { it.id }, wf.steps.moveStep("s1", -1).map { it.id })   // already first
        assertEquals("renamed", wf.steps.mapStep("s5") { it.copy(title = "renamed") }.findStep("s5")!!.title)
    }

    @Test fun namedOutputsAreConfiguredOnTheProducerAndUsedByName() = fixture {
        val wf = Workflow("w", listOf(
            WorkflowStep("s1", ref("List"), outputs = listOf(
                StepOutput("firstId", SourceKind.Field, "items[0].id"), StepOutput("all", SourceKind.Field, "items"), StepOutput("code", SourceKind.Status),
                StepOutput("ctype", SourceKind.Header, "content-type"), StepOutput("whole", SourceKind.Body), StepOutput("nope", SourceKind.Field, "missing.deep"),
            )),
            WorkflowStep("s2", ref("Item"), listOf(
                StepInput(InputTarget.PathParam, "id", "{{s1.out.firstId}}"),
                StepInput(InputTarget.Query, "c", "{{s1.out.code}}-{{s1.out.ctype}}"),
                StepInput(InputTarget.Header, "X-Missing", "{{s1.out.unknown}}"),
            )),
            WorkflowStep("s3", kind = StepKind.Set, setOp = SetOp.Count, varName = "n", list = "{{s1.out.all}}"),
            WorkflowStep("s4", kind = StepKind.Loop, list = "{{s1.out.all}}", children = listOf(
                WorkflowStep("s5", ref("Item"), listOf(StepInput(InputTarget.PathParam, "id", "{{item.id}}")), outputs = listOf(StepOutput("seen", SourceKind.Field, "path"))),
            )),
            WorkflowStep("s6", ref("Item"), listOf(StepInput(InputTarget.PathParam, "id", "last:{{s5.out.seen}}"))),
        ), stopOnFail = false)
        val (runs, events) = run(wf)
        val s2 = runs.first { it.step.id == "s2" }
        assertEquals("/item/1", Json.obj(Json.parse(s2.result!!.response!!.text))!!["path"])
        assertTrue(s2.result!!.request!!.url.contains("c=200-application%2Fjson"), s2.result!!.request!!.url)
        assertTrue(s2.notes.any { it.contains("does not pass on an output called 'unknown'") }, s2.notes.toString())
        val t = texts(events)
        assertTrue(t.any { it.contains("passes on") && it.contains("firstId = 1") && it.contains("code = 200") }, t.toString())
        assertTrue(runs.first { it.step.id == "s1" }.notes.any { it.contains("output 'nope'") }, runs.first { it.step.id == "s1" }.notes.toString())
        assertTrue(t.any { it.contains("n = 3") }, t.toString())
        // a loop overwrites its outputs every round; the step after the loop sees the last one
        assertEquals("/item/last:/item/3", Json.obj(Json.parse(runs.last().result!!.response!!.text))!!["path"])
        assertEquals(3, runs.count { it.step.id == "s5" })
        // the preview helper used by the builder reads the same value
        assertEquals("1", StepOutput("x", SourceKind.Field, "items[0].id").valueIn(runs.first().result).first)
        assertNotNull(StepOutput("x", SourceKind.Field, "zzz").valueIn(runs.first().result).second)
    }

    @Test fun outputsRoundTripAndShowFriendlyNames() {
        val wf = Workflow("o", listOf(WorkflowStep("s1", "a.yaml", outputs = listOf(
            StepOutput("id", SourceKind.Field, "data.id"), StepOutput("raw", SourceKind.Body), StepOutput("st", SourceKind.Status), StepOutput("loc", SourceKind.Header, "Location"), StepOutput("t", SourceKind.Time), StepOutput("r", SourceKind.Response),
        ))))
        val dir = createTempDirectory("wfo")
        WorkflowStore.save(dir, wf)
        assertEquals(wf, WorkflowStore.load(dir).single())
        assertEquals("[Login › id]", Slots.friendly("{{s1.out.id}}", wf.steps) { "Login" })
        assertEquals("s1.body.data.id", wf.steps[0].outputs[0].reference("s1"))
        assertEquals("s1.header.Location", wf.steps[0].outputs[3].reference("s1"))
    }

    @Test fun passedOnValuesAreAlsoPlainVariables() = fixture {
        val wf = Workflow("w", listOf(
            WorkflowStep("s1", ref("List"), outputs = listOf(StepOutput("first", SourceKind.Field, "items[0]"), StepOutput("orders", SourceKind.Field, "items"), StepOutput("ok", SourceKind.Status))),
            // {{first.id}} reads a field of a JSON output, {{ok}} a plain one, {{orders}} a whole list
            WorkflowStep("s2", ref("Item"), listOf(StepInput(InputTarget.PathParam, "id", "{{first.id}}-{{first.name}}"), StepInput(InputTarget.Query, "s", "{{ok}}"))),
            WorkflowStep("s3", kind = StepKind.Set, setOp = SetOp.Count, varName = "n", list = "{{orders}}"),
            WorkflowStep("s4", kind = StepKind.If, conditions = listOf(Condition("{{first.price}}", CondOp.Gt, "10")), children = listOf(WorkflowStep("s5", kind = StepKind.Set, varName = "big", value = "yes")), otherwise = listOf(WorkflowStep("s6", kind = StepKind.Set, varName = "big", value = "no"))),
        ))
        val (runs, events) = run(wf)
        val sent = runs.first { it.step.id == "s2" }.result!!.request!!.url
        assertTrue(sent.contains("/item/1-a") && sent.endsWith("s=200"), sent)
        val t = texts(events)
        assertTrue(t.any { it.contains("n = 3") }, t.toString())
        assertTrue(t.any { it.contains("big = yes") }, t.toString())     // first.price is 10.5
    }

    @Test fun plainOutputNamesAreShownFriendly() {
        val steps = listOf(WorkflowStep("s1", "a.yaml", outputs = listOf(StepOutput("token", SourceKind.Field, "t"), StepOutput("user", SourceKind.Field, "u"))), WorkflowStep("s2", "b.yaml"))
        val name = { st: WorkflowStep -> if (st.id == "s1") "Login" else "Other" }
        assertEquals("Bearer [Login › token]", Slots.friendlyAll("Bearer {{token}}", steps, name))
        assertEquals("[Login › user.id] and {{baseUrl}}", Slots.friendlyAll("{{user.id}} and {{baseUrl}}", steps, name))
        assertEquals("[Login › token]", Slots.friendlyAll("{{s1.out.token}}", steps, name))
    }

    @Test fun singleBracesUsePassedOnValuesAndLeaveEverythingElseAlone() = fixture {
        Collections.createRequest(col.path, ApiRequest(
            name = "Echo", method = "POST", url = "$base/echo/{id}/{nope}/:kind?who={user.name}&env={{baseUrl}}",
            headers = listOf(KV("X-Tok", "Bearer {token}"), KV("X-Env", "{{baseUrl}}")),
            bodyMode = BodyMode.Json, bodyJson = """{"id":{id},"name":"{user.name}","literal":{"a":1},"keep":"{unknown}","nested":{"id":{id}}}""",
        ))
        val c = Collections.load(col.path)
        val fx = Fixture(c, base, srv)
        val wf = Workflow("w", listOf(
            WorkflowStep("s1", fx.ref("List"), outputs = listOf(StepOutput("id", SourceKind.Field, "items[1].id"), StepOutput("user", SourceKind.Field, "items[0]"), StepOutput("token", SourceKind.Status))),
            WorkflowStep("s2", fx.ref("Echo"), listOf(StepInput(InputTarget.PathParam, "kind", "k-{index}"))),
            WorkflowStep("s3", kind = StepKind.If, conditions = listOf(Condition("{id}", CondOp.Eq, "2"), Condition("{user.price}", CondOp.Gt, "10")),
                children = listOf(WorkflowStep("s4", kind = StepKind.Set, varName = "msg", value = "id={id};{unknown}"))),
        ), stopOnFail = false)
        val (runs, events) = fx.run(wf)
        val sent = runs.first { it.step.id == "s2" }.result!!.request!!
        assertTrue(sent.url.contains("/echo/2/%7Bnope%7D/k-%7Bindex%7D?"), sent.url)   // {id} filled; {nope} and {index} (not passed-on names here) left as typed (only URL-encoded)
        assertTrue(sent.url.contains("who=a"), sent.url)                               // {user.name} read a field of a JSON value
        assertEquals("Bearer 200", sent.headers.first { it.first == "X-Tok" }.second)
        val body = Json.obj(Json.parse((sent.body as Body.Raw).text))!!
        assertEquals(2L, (body["id"] as Number).toLong())                              // stays a JSON number
        assertEquals("a", body["name"])
        assertEquals(mapOf("a" to 1L), Json.obj(body["literal"])!!.mapValues { (it.value as Number).toLong() })   // real JSON braces left alone
        assertEquals("{unknown}", body["keep"])
        assertEquals(2L, (Json.obj(body["nested"])!!["id"] as Number).toLong())
        val t = texts(events)
        assertTrue(t.any { it.contains("→ then") }, t.toString())                      // {id} = 2 and {user.price} = 10.5 > 10
        assertTrue(t.any { it.contains("msg = id=2;{unknown}") }, t.toString())
    }

    @Test fun loopVariablesAndSetVariablesWorkWithSingleBraces() = fixture {
        val wf = Workflow("w", listOf(
            step("s1", ref("List")),
            WorkflowStep("s2", kind = StepKind.Set, setOp = SetOp.Count, varName = "total", list = "{{s1.body.items}}"),
            WorkflowStep("s3", kind = StepKind.Loop, list = "{{s1.body.items}}", itemVar = "row", children = listOf(
                step("s4", ref("Item"), StepInput(InputTarget.PathParam, "id", "{row.id}"), StepInput(InputTarget.Query, "of", "{index}/{total}")),
            )),
        ))
        val items = run(wf).first.filter { it.step.id == "s4" }
        assertEquals(listOf("of=0%2F3", "of=1%2F3", "of=2%2F3"), items.map { it.result!!.request!!.url.substringAfter('?') })
        assertEquals(listOf("/item/1", "/item/2", "/item/3"), items.map { Json.obj(Json.parse(it.result!!.response!!.text))!!["path"] })
    }

    @Test fun runLogEventsCarryStructureForTheLogView() = fixture {
        val wf = Workflow("w", listOf(
            WorkflowStep("s1", ref("List"), outputs = listOf(StepOutput("n", SourceKind.Status))),
            WorkflowStep("s2", kind = StepKind.Loop, list = "{{s1.body.items}}", children = listOf(step("s3", ref("Item"), StepInput(InputTarget.PathParam, "id", "{item.id}")))),
            step("s4", "gone.yaml"),
        ), stopOnFail = false)
        val ev = run(wf).second
        val req = ev.first { it.stepId == "s1" && !it.sub }
        assertEquals("List", req.label); assertEquals(200, req.status); assertNotNull(req.ms)
        val passOn = ev.first { it.sub }
        assertTrue(passOn.text.startsWith("passes on n = 200") && passOn.stepId == "s1" && passOn.depth == req.depth)
        assertEquals(listOf("n" to "200"), passOn.fields)                                 // the table behind the log line
        val rounds = ev.filter { it.stepId == "s3" }
        assertEquals(3, rounds.size); assertTrue(rounds.all { it.depth == 1 && it.label == "Item" && it.status == 200 })
        assertEquals(0, ev.first { it.stepId == "s2" }.depth)
        assertTrue(ev.zipWithNext().all { (a, b) -> a.at <= b.at })               // time only moves forward
        val missing = ev.first { it.stepId == "s4" }
        assertFalse(missing.ok); assertNull(missing.status)
    }

    @Test fun flowRefsFindThePassedOnNamesATextUses() {
        assertEquals(listOf("token", "id"), FlowRefs.names("http://h/users/{id}?t={token}".let { "t={token}&u=$it" }))      // order of first use
        assertEquals(listOf("user", "n"), FlowRefs.names("{user.id} {{n}} {user.name} {user}"))                              // dotted paths and older {{n}} count, once each
        assertEquals(listOf("x"), FlowRefs.names("""{"a": 1, "b": {"c": 2}} {{ x.y }}-{ spaced }"""))                          // JSON and "{ spaced }" are not references; {{ x.y }} is (spaces allowed inside {{ }})
    }
}
