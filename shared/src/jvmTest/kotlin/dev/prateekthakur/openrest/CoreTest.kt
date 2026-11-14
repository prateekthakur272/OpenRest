package dev.prateekthakur.openrest

import dev.prateekthakur.openrest.core.*
import kotlin.io.path.*
import kotlin.test.*

class CoreTest {
    @Test fun bruRoundTrip() {
        val r = ApiRequest(
            name = "Create user", seq = 3, method = "POST", url = "{{host}}/users?a=1",
            params = listOf(KV("a", "1")), headers = listOf(KV("X-A", "b"), KV("X-Off", "c", false)),
            bodyMode = BodyMode.Json, bodyJson = "{\n  \"a\": 1\n}",
            auth = Auth(AuthMode.Bearer, token = "tok"),
            postVars = listOf(KV("id", "res.body.id")), assertions = listOf(KV("res.status", "eq 201")),
            tests = "test('x', () => {\n  expect(1).to.equal(1);\n});",
            multipart = listOf(KV("f", "/tmp/x", isFile = true), KV("t", "v")),
        )
        val back = Bru.parseRequest(Bru.serializeRequest(r))
        assertEquals(r.copy(), back)
    }

    @Test fun jsonPretty() {
        assertEquals("{\n  \"a\": [\n    1,\n    2\n  ],\n  \"b\": {}\n}", Json.prettyText("{\"a\":[1,2],\"b\":{}}"))
        assertNull(Json.prettyText("{bad"))
    }

    @Test fun curlParse() {
        val r = Importers.parseCurl("curl -X POST 'https://x.io/a?b=1' -H 'Content-Type: application/json' -d '{\"a\":1}' -u u:p")!!
        assertEquals("POST", r.method); assertEquals(BodyMode.Json, r.bodyMode); assertEquals(AuthMode.Basic, r.auth.mode)
        assertEquals("b", r.params[0].name)
    }

    @Test fun codegen() {
        val p = PreparedRequest("POST", "https://x.io", listOf("A" to "b"), Body.Raw("{}", "application/json"))
        CodeGen.languages.forEach { assertTrue(CodeGen.generate(it, p).contains("x.io"), it) }
    }

    @Test fun collectionAndRun() {
        val tmp = createTempDirectory("or")
        val dir = Collections.create(tmp, "Demo")
        Collections.createRequest(dir, ApiRequest(name = "One", url = "http://localhost:1/{{x}}", tests = "test('t', () => { expect(bru.getVar('q')).to.equal(undefined) })"))
        val col = Collections.load(dir)
        assertEquals(1, col.allRequests().size)
        val rc = RunCtx(col, null, mutableMapOf(), AppSettings())
        val res = Execution(rc, col.allRequests()[0]).run()
        assertNotNull(res.error) // connection refused
    }

    @Test fun scripts() {
        val tmp = createTempDirectory("or")
        val dir = Collections.create(tmp, "S")
        val srv = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        srv.createContext("/") { ex ->
            val b = "{\"id\": 7, \"items\": [1,2,3]}".toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.responseHeaders.add("X-Echo", ex.requestHeaders.getFirst("X-Pre") ?: "none")
            ex.sendResponseHeaders(200, b.size.toLong()); ex.responseBody.write(b); ex.close()
        }
        srv.start()
        try {
            val port = srv.address.port
            Collections.createRequest(dir, ApiRequest(
                name = "R", url = "http://127.0.0.1:$port/a", preScript = "req.setHeader('X-Pre', 'hello'); bru.setVar('k', 5)",
                postVars = listOf(KV("userId", "res.body.id")),
                assertions = listOf(KV("res.status", "eq 200"), KV("res.body.items", "length 3"), KV("res.body.id", "eq 8")),
                tests = "test('id', async () => { expect(res.body.id).to.equal(7); expect(res.getHeader('x-echo')).to.equal('hello'); expect(bru.getVar('userId')).to.equal('7') }); test('fails', () => { expect(1).to.equal(2) })",
            ))
            val col = Collections.load(dir)
            val res = Execution(RunCtx(col, null, mutableMapOf(), AppSettings()), col.allRequests()[0]).run()
            assertNull(res.error, res.error)
            assertEquals(200, res.response!!.status)
            val byName = res.tests.associateBy { it.name }
            println(res.tests)
            assertTrue(byName["res.status: eq 200"]!!.passed)
            assertTrue(byName["res.body.items: length 3"]!!.passed)
            assertFalse(byName["res.body.id: eq 8"]!!.passed)
            assertTrue(byName["id"]!!.passed, byName["id"]!!.message)
            assertFalse(byName["fails"]!!.passed)
        } finally { srv.stop(0) }
    }
}
