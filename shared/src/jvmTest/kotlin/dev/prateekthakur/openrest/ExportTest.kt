package dev.prateekthakur.openrest

import dev.prateekthakur.openrest.core.interop.*
import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.model.Collection
import dev.prateekthakur.openrest.core.storage.*
import dev.prateekthakur.openrest.utils.*

import kotlin.io.path.*
import kotlin.test.*

class ExportTest {
    private val sample = ApiRequest(
        name = "Create user", seq = 2, method = "POST", url = "{{baseUrl}}/users/:id?a=1",
        params = listOf(KV("a", "1"), KV("off", "x", false)), pathParams = listOf(KV("id", "5")),
        headers = listOf(KV("X-A", "b: c"), KV("X-Off", "c", false)),
        bodyMode = BodyMode.Json, bodyJson = "{\n  \"name\": \"Ada\",\n  \"n\": 1\n}", bodyText = "note: with colon",
        multipart = listOf(KV("f", "/tmp/x", isFile = true, contentType = "text/plain"), KV("t", "v")),
        auth = Auth(AuthMode.Bearer, token = "tok"),
        preVars = listOf(KV("a", "true")), postVars = listOf(KV("id", "res.body.id")),
        assertions = listOf(KV("res.status", "eq 201")),
        preScript = "bru.setVar('x', 1);\n  // indented", tests = "test('x', () => {\n  expect(1).to.equal(1);\n});", docs = "# Title\n\ntext",
        timeoutMs = 5000, encodeUrl = false,
    )

    private fun collection(): Collection {
        System.setProperty("user.home", createTempDirectory("h").toString())
        val dir = Collections.create(createTempDirectory("c"), "Shop API")
        Collections.saveCollectionConfig(dir, NodeConfig(name = "Shop API", auth = Auth(AuthMode.Bearer, token = "root"), preVars = listOf(KV("version", "v1")), docs = "hello"))
        Collections.saveEnvironment(dir, Environment("dev", listOf(KV("baseUrl", "https://api.shop.io/v1"), KV("key", "s3")), setOf("key")))
        val f = Collections.createFolder(dir, "Users")
        Collections.saveFolderConfig(f, NodeConfig(name = "Users", seq = 1, headers = listOf(KV("X-F", "1"))))
        Collections.createRequest(f, sample)
        Collections.createRequest(dir, ApiRequest(name = "Ping", url = "{{baseUrl}}/ping"))
        return Collections.load(dir)
    }

    @Test fun yamlRoundTrip() {
        assertEquals(sample, OrYaml.mapToRequest(Yml.parse(OrYaml.requestText(sample)) as Map<*, *>))
        val tmp = createTempFile().also { it.writeText(OrYaml.requestText(sample)) }
        println(tmp.readText())
    }

    @Test fun collectionOnDisk() {
        val c = collection()
        assertEquals("Shop API", c.name); assertEquals(AuthMode.Bearer, c.config.auth.mode); assertEquals("v1", c.config.preVars[0].value)
        assertEquals(2, c.allRequests().size)
        assertEquals(setOf("key"), c.environments[0].secrets)
        assertEquals("X-F", c.children.filterIsInstance<FolderNode>()[0].config.headers[0].name)
        assertTrue(c.path.resolve("openrest.yaml").exists())
    }

    @Test fun allFormats() {
        val c = collection()
        val out = createTempDirectory("exp")
        for (f in Exporters.formats) {
            val dest = if (f.isDirectory) out else out.resolve("x" + f.ext)
            val written = Exporters.export(f.id, c, dest)
            assertTrue(written.exists(), f.id)
            if (!f.isDirectory) assertTrue(written.readText().isNotBlank(), f.id)
        }
        val oa = Yml.parse(out.resolve("x.openapi.yaml").readText()) as Map<*, *>
        val paths = oa["paths"] as Map<*, *>
        assertTrue(paths.containsKey("/users/{id}"), paths.keys.toString()); assertTrue(paths.containsKey("/ping"))
        assertEquals("https://api.shop.io/v1", ((oa["servers"] as List<*>)[0] as Map<*, *>)["url"])
        assertTrue(Json.isValid(out.resolve("x.openapi.json").readText()))
        assertTrue(Json.isValid(out.resolve("x.insomnia.json").readText()))
        assertTrue(out.resolve("x.http").readText().contains("POST https://") || out.resolve("x.http").readText().contains("POST {{baseUrl}}"))
        println(out.resolve("x.openapi.yaml").readText())
    }

    @Test fun bundleRoundTrip() {
        val c = collection()
        val f = createTempDirectory("b").resolve("b.yaml")
        Exporters.export("bundle", c, f)
        val back = Collections.load(Importers.importBundle(f.readText(), createTempDirectory("bi")))
        assertEquals(c.allRequests().map { it.request.copy(seq = 0) }.sortedBy { it.name }, back.allRequests().map { it.request.copy(seq = 0) }.sortedBy { it.name })
        assertEquals(c.config.auth, back.config.auth)
        assertEquals(1, back.environments.size)
    }

    @Test fun brunoRoundTrip() {
        val c = collection()
        val out = createTempDirectory("br")
        val bru = Exporters.export("bruno", c, out)
        assertTrue(bru.resolve("bruno.json").exists())
        val back = Collections.load(Importers.importBruno(bru, createTempDirectory("brin")))
        assertEquals(2, back.allRequests().size)
        val r = back.allRequests().first { it.name == "Create user" }.request
        assertEquals(sample.copy(seq = r.seq, bodyText = r.bodyText, params = r.params).headers, r.headers)
        assertEquals("tok", r.auth.token); assertEquals(BodyMode.Json, r.bodyMode)
        assertEquals(AuthMode.Bearer, back.config.auth.mode)
    }

    @Test fun openApiRoundTrip() {
        val c = collection()
        val f = createTempDirectory("o").resolve("o.yaml")
        Exporters.export("openapi-yaml", c, f)
        val back = Collections.load(Importers.importOpenApi(f.readText(), createTempDirectory("oi")))
        assertEquals(2, back.allRequests().size)
    }
}
