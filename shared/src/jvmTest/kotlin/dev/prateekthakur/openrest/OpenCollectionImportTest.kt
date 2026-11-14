package dev.prateekthakur.openrest

import dev.prateekthakur.openrest.core.interop.*
import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.storage.*

import kotlin.io.path.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Bruno's newer YAML layout (opencollection.yml + one .yml per request). */
class OpenCollectionImportTest {
    private fun fixture() = createTempDirectory("oc").also { d ->
        d.resolve("opencollection.yml").writeText("opencollection: 1.0.0\n\ninfo:\n  name: Demo\nbundled: false\nextensions:\n  bruno:\n    ignore:\n      - node_modules\n      - .git\n")
        d.resolve("fetch sites.yml").writeText("""
            info:
              name: fetch sites
              type: http
              seq: 2

            http:
              method: POST
              url: https://example.com/a
              body:
                type: json
                data: |-
                  {
                    "source": "dropbox"
                  }
              auth: inherit

            settings:
              encodeUrl: true
              timeout: 0
        """.trimIndent())
        d.resolve("long url.yml").writeText("""
            info:
              name: long url
              type: http
              seq: 1
            http:
              method: GET
              url: |-
                https://example.com/sites/10437bed
                /folders-run
              headers:
                - name: Accept
                  value: application/json
                - name: X-Off
                  value: "1"
                  disabled: true
              params:
                - name: verbose
                  value: "true"
                  type: query
                - name: id
                  value: "7"
                  type: path
              auth:
                type: bearer
                token: '{{token}}'
            runtime:
              scripts:
                - type: before-request
                  code: req.setHeader('X-A', '1')
                - type: tests
                  code: test('ok', () => expect(res.status).to.equal(200))
              assertions:
                - expression: res.status
                  operator: eq
                  value: "200"
        """.trimIndent())
        d.resolve("Users").createDirectories()
        d.resolve("Users/folder.yml").writeText("info:\n  name: Users\n  type: folder\n  seq: 1\n")
        d.resolve("Users/get user.yml").writeText("info:\n  name: get user\n  type: http\n  seq: 1\nhttp:\n  method: get\n  url: https://example.com/u\n  auth: none\n")
        d.resolve("environments").createDirectories()
        d.resolve("environments/Local.yml").writeText("name: Local\nvariables:\n  - name: baseUrl\n    value: http://localhost\n  - name: token\n    secret: true\n")
    }

    @Test fun importsTheYamlLayoutOfABrunoCollection() {
        val src = fixture(); val out = createTempDirectory("oc-out")
        assertTrue(OpenCollectionImporter.isOpenCollection(src))
        val dir = Importers.importBruno(src, out)                                   // the Bruno entry point recognises the layout
        val c = Collections.load(dir)
        assertEquals("Demo", c.name)
        val reqs = c.allRequests().associateBy { it.name }
        assertEquals(setOf("fetch sites", "long url", "get user"), reqs.keys)
        val f = reqs.getValue("fetch sites").request
        assertEquals("POST", f.method); assertEquals(BodyMode.Json, f.bodyMode)
        assertTrue(f.bodyJson.contains("\"source\": \"dropbox\"")); assertEquals(AuthMode.Inherit, f.auth.mode)
        val l = reqs.getValue("long url").request
        assertEquals("https://example.com/sites/10437bed/folders-run", l.url.substringBefore("?"))        // the wrapped URL is joined
        assertEquals(listOf("Accept", "X-Off"), l.headers.map { it.name }); assertEquals(false, l.headers[1].enabled)
        assertEquals("true", l.params.single { it.name == "verbose" }.value); assertEquals("7", l.pathParams.single().value)
        assertEquals(AuthMode.Bearer, l.auth.mode); assertEquals("{{token}}", l.auth.token)
        assertTrue(l.preScript.contains("setHeader")); assertTrue(l.tests.contains("test('ok'"))
        assertEquals(listOf(KV("res.status", "eq 200")), l.assertions)
        assertEquals("GET", reqs.getValue("get user").request.method)
        assertEquals(1, c.children.filterIsInstance<FolderNode>().size)                             // folders are kept
        val env = assertNotNull(c.environments.singleOrNull { it.name == "Local" })
        assertEquals("http://localhost", env.vars.first { it.name == "baseUrl" }.value); assertEquals(setOf("token"), env.secrets)
    }

    @Test fun theOldBruLayoutStillWorks() {
        val src = createTempDirectory("bru"); src.resolve("bruno.json").writeText("""{"version":"1","name":"Old","type":"collection"}""")
        src.resolve("a.bru").writeText("meta {\n  name: a\n  type: http\n  seq: 1\n}\n\nget {\n  url: https://example.com\n  body: none\n  auth: none\n}\n")
        val c = Collections.load(Importers.importBruno(src, createTempDirectory("o")))
        assertEquals(listOf("a"), c.allRequests().map { it.name })
    }
}
