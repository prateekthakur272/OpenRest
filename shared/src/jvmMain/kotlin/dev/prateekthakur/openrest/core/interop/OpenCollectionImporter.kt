package dev.prateekthakur.openrest.core.interop

import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.storage.*
import dev.prateekthakur.openrest.utils.*

import java.nio.file.Path
import kotlin.io.path.*

/**
 * Imports a Bruno collection saved in the newer YAML "OpenCollection" layout: an `opencollection.yml` at the root,
 * one `.yml` file per request, `folder.yml` per folder and an `environments` folder of `.yml` files.
 * The reader is lenient: it understands the field names Bruno writes and ignores what it does not know.
 */
object OpenCollectionImporter {
    private val RESERVED = setOf("opencollection.yml", "opencollection.yaml", "folder.yml", "folder.yaml")

    fun isOpenCollection(dir: Path) = dir.isDirectory() && (dir.resolve("opencollection.yml").exists() || dir.resolve("opencollection.yaml").exists())

    fun import(src: Path, parent: Path): Path {
        val root = readMap(listOf("opencollection.yml", "opencollection.yaml").map { src.resolve(it) }.first { it.exists() }) ?: error("opencollection.yml is not valid YAML")
        val name = str(map(root["info"])?.get("name")).ifBlank { src.name }
        val ignore: Set<String> = (list(map(map(root["extensions"])?.get("bruno"))?.get("ignore")) ?: emptyList<Any?>()).map { str(it) }.toSet() + setOf("node_modules", ".git", "environments")
        val dir = Importers.newCollectionDir(parent, name)
        val w = Importers.Writer(dir)
        Collections.saveCollectionConfig(dir, nodeConfig(root, name))

        fun walk(from: Path, into: Path) {
            var folderSeq = 1
            val entries = from.listDirectoryEntries().sortedBy { it.name.lowercase() }
            for (e in entries) {
                if (e.name.startsWith(".") || e.name in ignore && e.isDirectory()) continue
                if (e.isDirectory()) {
                    val fm = listOf("folder.yml", "folder.yaml").map { e.resolve(it) }.firstOrNull { it.exists() }?.let { readMap(it) }
                    val cfg = nodeConfig(fm ?: emptyMap<String, Any?>(), e.name)
                    walk(e, w.folder(into, cfg.name.ifBlank { e.name }, if (cfg.seq <= 0 || cfg.seq == Int.MAX_VALUE) folderSeq++ else cfg.seq, cfg))
                } else if ((e.extension == "yml" || e.extension == "yaml") && e.name !in RESERVED) {
                    val m = readMap(e) ?: continue
                    if (m["http"] == null && m["info"] == null) continue
                    runCatching { request(m, e.nameWithoutExtension) }.getOrNull()?.let { w.request(into, it, it.seq) }
                }
            }
        }
        walk(src, dir)

        val envDir = src.resolve("environments")
        if (envDir.isDirectory()) envDir.listDirectoryEntries().filter { it.extension == "yml" || it.extension == "yaml" }.forEach { f ->
            readMap(f)?.let { m -> Collections.saveEnvironment(dir, OrYaml.mapToEnv(m, str(m["name"]).ifBlank { f.nameWithoutExtension })) }
        }
        return dir
    }

    // ---- one request ----

    fun request(m: Map<*, *>, fallbackName: String): ApiRequest {
        val info = map(m["info"]); val http = map(m["http"]) ?: emptyMap<String, Any?>()
        val settings = map(m["settings"])
        val body = map(http["body"])
        val type = str(body?.get("type")).lowercase()
        val data = body?.get("data")
        val mode = when (type) {
            "json" -> BodyMode.Json; "text" -> BodyMode.Text; "xml" -> BodyMode.Xml
            "form-urlencoded", "formurlencoded", "form_urlencoded" -> BodyMode.Form
            "multipart-form", "multipartform", "multipart_form", "multipart" -> BodyMode.Multipart
            "graphql" -> BodyMode.Graphql; "file" -> BodyMode.File
            else -> BodyMode.None
        }
        val params = list(http["params"])?.mapNotNull { map(it) }.orEmpty()
        val gql = map(data)
        val (pre, post, tests) = scripts(m)
        val url = str(http["url"]).lines().joinToString("") { it.trim() }          // long URLs are wrapped over several lines in the file
        return ApiRequest(
            name = str(info?.get("name")).ifBlank { fallbackName },
            seq = (info?.get("seq") as? Number)?.toInt() ?: 1,
            method = str(http["method"]).ifBlank { "GET" }.uppercase(),
            url = url,
            params = params.filter { str(it["type"]).lowercase() != "path" }.map(::kv),
            pathParams = params.filter { str(it["type"]).lowercase() == "path" }.map(::kv),
            headers = list(http["headers"])?.mapNotNull { map(it) }?.map(::kv).orEmpty(),
            bodyMode = mode,
            bodyJson = if (mode == BodyMode.Json) str(data) else "",
            bodyText = if (mode == BodyMode.Text) str(data) else "",
            bodyXml = if (mode == BodyMode.Xml) str(data) else "",
            bodyGraphql = if (mode == BodyMode.Graphql) str(gql?.get("query") ?: data) else "",
            bodyGraphqlVars = if (mode == BodyMode.Graphql) str(gql?.get("variables")) else "",
            bodyFile = if (mode == BodyMode.File) str(map(data)?.get("path") ?: data) else "",
            formUrlEncoded = if (mode == BodyMode.Form) list(data)?.mapNotNull { map(it) }?.map(::kv).orEmpty() else emptyList(),
            multipart = if (mode == BodyMode.Multipart) list(data)?.mapNotNull { map(it) }?.map { kv(it).copy(isFile = str(it["type"]).lowercase() == "file", contentType = str(it["contentType"])) }.orEmpty() else emptyList(),
            auth = auth(http["auth"], inherit = true),
            preVars = vars(m, "pre"), postVars = vars(m, "post"),
            assertions = assertions(m),
            preScript = pre, postScript = post, tests = tests,
            docs = docs(m["docs"]),
            encodeUrl = settings?.get("encodeUrl") as? Boolean ?: true,
            timeoutMs = (settings?.get("timeout") as? Number)?.toInt() ?: 0,
        )
    }

    // ---- collection and folder defaults ----

    private fun nodeConfig(m: Map<*, *>, fallbackName: String): NodeConfig {
        val info = map(m["info"])
        // defaults may sit under `request:` (collection / folder) or `http:`, depending on the version that wrote the file
        val d = map(m["request"]) ?: map(m["http"]) ?: emptyMap<String, Any?>()
        val rt: Map<*, *> = if (m["runtime"] != null) m else mapOf<String, Any?>("runtime" to (d["runtime"] ?: d))
        val (pre, post, tests) = scripts(rt)
        return NodeConfig(
            name = str(info?.get("name")).ifBlank { fallbackName },
            seq = (info?.get("seq") as? Number)?.toInt() ?: Int.MAX_VALUE,
            headers = list(d["headers"])?.mapNotNull { map(it) }?.map(::kv).orEmpty(),
            auth = auth(d["auth"], inherit = false),
            preVars = vars(rt, "pre"), postVars = vars(rt, "post"),
            preScript = pre, postScript = post, tests = tests,
            docs = docs(m["docs"]),
        )
    }

    // ---- pieces ----

    private fun auth(v: Any?, inherit: Boolean): Auth {
        if (v == null) return Auth(if (inherit) AuthMode.Inherit else AuthMode.None)
        if (v is String) return Auth(when (v.lowercase()) { "inherit" -> AuthMode.Inherit; "none" -> AuthMode.None; else -> AuthMode.None })
        val a = map(v) ?: return Auth(AuthMode.None)
        return when (str(a["type"]).lowercase()) {
            "inherit" -> Auth(AuthMode.Inherit)
            "bearer" -> Auth(AuthMode.Bearer, token = str(a["token"]))
            "basic" -> Auth(AuthMode.Basic, username = str(a["username"]), password = str(a["password"]))
            "digest" -> Auth(AuthMode.Digest, username = str(a["username"]), password = str(a["password"]))
            "apikey", "api-key", "api_key" -> Auth(AuthMode.ApiKey, keyName = str(a["key"]), keyValue = str(a["value"]), placement = if (str(a["placement"]).lowercase().startsWith("query")) "queryparams" else "header")
            "oauth2" -> Auth(
                AuthMode.OAuth2, grantType = str(a["flow"] ?: a["grant_type"] ?: a["grantType"]).ifBlank { "client_credentials" }.replace("-", "_").let { if (it.contains("password")) "password" else "client_credentials" },
                tokenUrl = str(a["access_token_url"] ?: a["accessTokenUrl"] ?: a["tokenUrl"]), clientId = str(a["client_id"] ?: a["clientId"]), clientSecret = str(a["client_secret"] ?: a["clientSecret"]),
                scope = str(a["scope"]), username = str(a["username"]), password = str(a["password"]),
            )
            else -> Auth(AuthMode.None)
        }
    }

    /** `runtime.scripts` is a list of `{type, code}`; older files used a map keyed by the same names. */
    private fun scripts(m: Map<*, *>): Triple<String, String, String> {
        val rt = map(m["runtime"]) ?: m
        var pre = ""; var post = ""; var tests = ""
        fun put(type: String, code: String) {
            when (type.lowercase().replace("_", "-")) { "before-request", "pre-request", "pre" -> pre = code; "after-response", "post-response", "post" -> post = code; "tests", "test" -> tests = code }
        }
        when (val s = rt["scripts"]) {
            is List<*> -> s.mapNotNull { map(it) }.forEach { put(str(it["type"]), str(it["code"] ?: it["script"])) }
            is Map<*, *> -> s.forEach { (k, v) -> put(str(k), if (v is Map<*, *>) str(v["code"]) else str(v)) }
        }
        return Triple(pre, post, tests)
    }

    private fun vars(m: Map<*, *>, which: String): List<KV> {
        val rt = map(m["runtime"]) ?: m
        val v = rt["vars"]
        val want = if (which == "pre") setOf("pre-request", "pre", "before-request", "request") else setOf("post-response", "post", "after-response", "response")
        return when (v) {
            is List<*> -> v.mapNotNull { map(it) }.filter { str(it["type"] ?: it["when"]).lowercase().ifBlank { "pre-request" } in want }.map(::kv)
            is Map<*, *> -> list(v.entries.firstOrNull { str(it.key).lowercase() in want }?.value)?.mapNotNull { map(it) }?.map(::kv).orEmpty()
            else -> emptyList()
        }
    }

    private fun assertions(m: Map<*, *>): List<KV> {
        val rt = map(m["runtime"]) ?: m
        return list(rt["assertions"])?.mapNotNull { map(it) }?.map { a ->
            val expr = str(a["expression"] ?: a["name"])
            val op = str(a["operator"])
            val value = str(a["value"])
            KV(expr, if (op.isBlank()) value else "$op $value".trim(), a["disabled"] != true && a["enabled"] != false)
        }.orEmpty()
    }

    private fun docs(v: Any?): String = if (v is Map<*, *>) str(v["content"]) else str(v)

    private fun kv(m: Map<*, *>) = KV(str(m["name"]), str(m["value"]), m["disabled"] != true && m["enabled"] != false)

    // ---- helpers ----

    private fun readMap(p: Path): Map<*, *>? = runCatching { Yml.parse(p.readText()) as? Map<*, *> }.getOrNull()
    private fun map(v: Any?): Map<*, *>? = v as? Map<*, *>
    private fun list(v: Any?): List<*>? = v as? List<*>
    private fun str(v: Any?): String = when (v) { null -> ""; is String -> v; is Map<*, *>, is List<*> -> Json.stringify(v); else -> v.toString() }
}
