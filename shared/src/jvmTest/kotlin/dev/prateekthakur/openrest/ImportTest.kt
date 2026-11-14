package dev.prateekthakur.openrest

import dev.prateekthakur.openrest.core.interop.*
import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.storage.*
import dev.prateekthakur.openrest.utils.*

import kotlin.io.path.*
import kotlin.test.*

class ImportTest {
    @Test fun postman() {
        val json = """{"info":{"name":"Pm Col","schema":"x"},"variable":[{"key":"host","value":"https://x.io"}],
          "auth":{"type":"bearer","bearer":[{"key":"token","value":"abc"}]},
          "item":[{"name":"Folder","item":[{"name":"Create","event":[{"listen":"test","script":{"exec":["pm.test('ok', function(){ pm.expect(pm.response.code).to.equal(200) })"]}}],
            "request":{"method":"POST","header":[{"key":"X-A","value":"1"}],"url":{"raw":"{{host}}/a?b=1","query":[{"key":"b","value":"1"}]},
            "body":{"mode":"raw","raw":"{\"a\":1}","options":{"raw":{"language":"json"}}}}}]},
            {"name":"Plain","request":{"method":"GET","url":"{{host}}/p"}}]}"""
        val dir = Importers.importPostman(json, createTempDirectory("pm"))
        val col = Collections.load(dir)
        assertEquals("Pm Col", col.name)
        assertEquals(AuthMode.Bearer, col.config.auth.mode)
        assertEquals("host", col.config.preVars[0].name)
        val reqs = col.allRequests()
        assertEquals(2, reqs.size)
        val create = reqs.first { it.name == "Create" }.request
        assertEquals(BodyMode.Json, create.bodyMode)
        assertTrue(create.tests.contains("expect(res.status)"))
        assertEquals("b", create.params[0].name)
        assertEquals(1, col.children.filterIsInstance<FolderNode>().size)
    }

    @Test fun openapi() {
        val spec = """{"openapi":"3.0.0","info":{"title":"Pets"},"servers":[{"url":"https://pets.io/v1"}],
          "paths":{"/pets/{id}":{"get":{"tags":["pets"],"summary":"Get pet","parameters":[{"name":"id","in":"path","required":true,"schema":{"type":"integer"}}]},
          "put":{"tags":["pets"],"operationId":"updPet","requestBody":{"content":{"application/json":{"schema":{"${'$'}ref":"#/components/schemas/Pet"}}}}}}},
          "components":{"schemas":{"Pet":{"type":"object","properties":{"name":{"type":"string"},"age":{"type":"integer"}}}}}}"""
        val dir = Importers.importOpenApi(spec, createTempDirectory("oa"))
        val col = Collections.load(dir)
        assertEquals("baseUrl", col.environments[0].vars[0].name)
        val put = col.allRequests().first { it.request.method == "PUT" }.request
        assertEquals("{{baseUrl}}/pets/:id", put.url)
        assertTrue(put.bodyJson.contains("\"name\": \"string\""))
    }

    @Test fun exportRoundTrip() {
        val dir = Importers.importPostman("""{"info":{"name":"X"},"item":[{"name":"A","request":{"method":"GET","url":"http://a"}}]}""", createTempDirectory("ex"))
        val out = Importers.exportPostman(Collections.load(dir))
        assertTrue(Json.isValid(out) && out.contains("http://a"))
    }

    @Test fun urlSync() {
        val ps = UrlSync.paramsFromUrl("http://a/b?x=1&y=two%20words", emptyList())
        assertEquals(listOf("x", "y"), ps.map { it.name }); assertEquals("two words", ps[1].value)
        assertEquals("http://a/b?x=1&y=two%20words", UrlSync.urlWithParams("http://a/b?old=1", ps))
    }

    @Test fun envSecrets() {
        System.setProperty("user.home", createTempDirectory("h").toString())
        val dir = Collections.create(createTempDirectory("c"), "S")
        Collections.saveEnvironment(dir, Environment("dev", listOf(KV("a", "1"), KV("tok", "s3cret")), setOf("tok")))
        assertFalse(dir.resolve("environments/dev.yaml").readText().contains("s3cret"))
        val e = Collections.load(dir).environments[0]
        assertEquals(setOf("tok"), e.secrets)
    }
}
