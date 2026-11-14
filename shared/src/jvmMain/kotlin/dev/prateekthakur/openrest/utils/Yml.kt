package dev.prateekthakur.openrest.utils

import dev.prateekthakur.openrest.core.model.*

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.nodes.Tag
import org.yaml.snakeyaml.representer.Represent
import org.yaml.snakeyaml.representer.Representer

/** Thin SnakeYAML wrapper: parse to plain maps/lists, dump with readable block style (multi-line strings as `|`). */
object Yml {
    private class R(o: DumperOptions) : Representer(o) {
        init {
            val orig = representers[String::class.java]!!
            representers[String::class.java] = Represent { data ->
                val s = data as String
                if (s.contains('\n') && !s.contains('\r')) representScalar(Tag.STR, s, DumperOptions.ScalarStyle.LITERAL) else orig.representData(data)
            }
        }
    }

    private val dumper: Yaml by lazy {
        val o = DumperOptions()
        o.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK)
        o.setIndent(2)
        o.setIndicatorIndent(0)
        o.setSplitLines(false)
        o.setAllowUnicode(true)
        Yaml(R(o), o)
    }

    fun parse(text: String): Any? = Yaml(SafeConstructor(LoaderOptions())).load<Any?>(text)

    fun tryParse(text: String): Any? = try { parse(text) } catch (_: Exception) { null }

    fun dump(v: Any?): String = synchronized(this) { dumper.dump(v) }

    /** Map builder that drops null / empty values so files stay tidy. */
    fun m(vararg p: Pair<String, Any?>): LinkedHashMap<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        for ((k, v) in p) {
            val empty = v == null || (v is String && v.isEmpty()) || (v is List<*> && v.isEmpty()) || (v is Map<*, *> && v.isEmpty())
            if (!empty) out[k] = v
        }
        return out
    }
}

/** Mapping between OpenRest's model and the OpenRest YAML file format. */
object OrYaml {
    const val ROOT_FILE = "openrest.yaml"
    const val FOLDER_FILE = "folder.yaml"
    const val VERSION = 1

    private fun s(v: Any?): String = v?.toString() ?: ""
    private fun map(v: Any?): Map<*, *>? = v as? Map<*, *>

    fun kvOut(l: List<KV>): List<Map<String, Any?>> = l.map {
        Yml.m("name" to it.name, "value" to it.value, "enabled" to (if (it.enabled) null else false), "file" to (if (it.isFile) true else null), "contentType" to it.contentType)
    }

    fun kvIn(v: Any?): List<KV> = when (v) {
        is List<*> -> v.mapNotNull { e -> map(e)?.let { m -> KV(s(m["name"]), s(m["value"]), m["enabled"] != false, m["file"] == true, s(m["contentType"])) } }
        is Map<*, *> -> v.map { (k, x) -> KV(k.toString(), s(x)) }
        else -> emptyList()
    }

    private fun authOut(a: Auth, omit: AuthMode): Map<String, Any?>? {
        if (a.mode == omit) return null
        return when (a.mode) {
            AuthMode.None, AuthMode.Inherit -> Yml.m("type" to a.mode.bru)
            AuthMode.Basic, AuthMode.Digest -> Yml.m("type" to a.mode.bru, "username" to a.username, "password" to a.password)
            AuthMode.Bearer -> Yml.m("type" to "bearer", "token" to a.token)
            AuthMode.ApiKey -> Yml.m("type" to "apikey", "key" to a.keyName, "value" to a.keyValue, "placement" to a.placement)
            AuthMode.OAuth2 -> Yml.m(
                "type" to "oauth2", "grantType" to a.grantType, "tokenUrl" to a.tokenUrl, "clientId" to a.clientId,
                "clientSecret" to a.clientSecret, "scope" to a.scope, "username" to a.username, "password" to a.password,
            )
        }
    }

    private fun authIn(v: Any?, default: AuthMode): Auth {
        val m = map(v) ?: return Auth(default)
        return Auth(
            mode = AuthMode.fromBru(s(m["type"]).ifEmpty { default.bru }),
            username = s(m["username"]), password = s(m["password"]), token = s(m["token"]),
            keyName = s(m["key"]), keyValue = s(m["value"]), placement = s(m["placement"]).ifEmpty { "header" },
            grantType = s(m["grantType"]).ifEmpty { "client_credentials" }, tokenUrl = s(m["tokenUrl"]),
            clientId = s(m["clientId"]), clientSecret = s(m["clientSecret"]), scope = s(m["scope"]),
        )
    }

    private fun txt(v: Any?) = s(v).trimEnd()

    fun requestToMap(r: ApiRequest): Map<String, Any?> {
        val hasBody = r.bodyMode != BodyMode.None || listOf(r.bodyJson, r.bodyText, r.bodyXml, r.bodyGraphql, r.bodyGraphqlVars, r.bodyFile).any { it.isNotEmpty() } ||
            r.formUrlEncoded.isNotEmpty() || r.multipart.isNotEmpty()
        return Yml.m(
            "name" to r.name, "seq" to r.seq.toLong(), "method" to r.method, "url" to r.url,
            "params" to Yml.m("query" to kvOut(r.params), "path" to kvOut(r.pathParams)),
            "headers" to kvOut(r.headers),
            "body" to if (!hasBody) null else Yml.m(
                "type" to r.bodyMode.bru, "json" to r.bodyJson, "text" to r.bodyText, "xml" to r.bodyXml,
                "graphql" to r.bodyGraphql, "graphqlVariables" to r.bodyGraphqlVars, "form" to kvOut(r.formUrlEncoded),
                "multipart" to kvOut(r.multipart), "file" to r.bodyFile,
            ),
            "auth" to authOut(r.auth, AuthMode.Inherit),
            "vars" to Yml.m("pre" to kvOut(r.preVars), "post" to kvOut(r.postVars)),
            "assertions" to kvOut(r.assertions),
            "scripts" to Yml.m("pre" to r.preScript, "post" to r.postScript),
            "tests" to r.tests, "docs" to r.docs,
            "settings" to Yml.m("encodeUrl" to (if (r.encodeUrl) null else false), "timeout" to (if (r.timeoutMs > 0) r.timeoutMs.toLong() else null)),
        )
    }

    fun mapToRequest(m: Map<*, *>, fallbackName: String = "Request"): ApiRequest {
        val params = map(m["params"]); val body = map(m["body"]); val vars = map(m["vars"]); val scripts = map(m["scripts"]); val st = map(m["settings"])
        return ApiRequest(
            name = s(m["name"]).ifEmpty { fallbackName },
            seq = (m["seq"] as? Number)?.toInt() ?: 1,
            method = s(m["method"]).ifEmpty { "GET" }.uppercase(),
            url = s(m["url"]),
            params = kvIn(params?.get("query")), pathParams = kvIn(params?.get("path")),
            headers = kvIn(m["headers"]),
            bodyMode = BodyMode.fromBru(s(body?.get("type")).ifEmpty { "none" }),
            bodyJson = txt(body?.get("json")), bodyText = txt(body?.get("text")), bodyXml = txt(body?.get("xml")),
            bodyGraphql = txt(body?.get("graphql")), bodyGraphqlVars = txt(body?.get("graphqlVariables")), bodyFile = s(body?.get("file")),
            formUrlEncoded = kvIn(body?.get("form")), multipart = kvIn(body?.get("multipart")),
            auth = authIn(m["auth"], AuthMode.Inherit),
            preVars = kvIn(vars?.get("pre")), postVars = kvIn(vars?.get("post")),
            assertions = kvIn(m["assertions"]),
            preScript = txt(scripts?.get("pre")), postScript = txt(scripts?.get("post")),
            tests = txt(m["tests"]), docs = txt(m["docs"]),
            encodeUrl = st?.get("encodeUrl") != false, timeoutMs = (st?.get("timeout") as? Number)?.toInt() ?: 0,
        )
    }

    fun configToMap(c: NodeConfig, folder: Boolean): Map<String, Any?> = Yml.m(
        "name" to c.name, "seq" to (if (folder) c.seq.toLong() else null),
        "headers" to kvOut(c.headers), "auth" to authOut(c.auth, AuthMode.None),
        "vars" to Yml.m("pre" to kvOut(c.preVars), "post" to kvOut(c.postVars)),
        "scripts" to Yml.m("pre" to c.preScript, "post" to c.postScript),
        "tests" to c.tests, "docs" to c.docs,
    )

    fun mapToConfig(m: Map<*, *>, fallbackName: String = ""): NodeConfig {
        val vars = map(m["vars"]); val scripts = map(m["scripts"])
        return NodeConfig(
            name = s(m["name"]).ifEmpty { fallbackName }, seq = (m["seq"] as? Number)?.toInt() ?: Int.MAX_VALUE,
            headers = kvIn(m["headers"]), auth = authIn(m["auth"], AuthMode.None),
            preVars = kvIn(vars?.get("pre")), postVars = kvIn(vars?.get("post")),
            preScript = txt(scripts?.get("pre")), postScript = txt(scripts?.get("post")),
            tests = txt(m["tests"]), docs = txt(m["docs"]),
        )
    }

    fun envToMap(e: Environment): Map<String, Any?> = Yml.m(
        "name" to e.name,
        "variables" to e.vars.map {
            val secret = it.name in e.secrets
            Yml.m("name" to it.name, "value" to (if (secret) null else it.value), "enabled" to (if (it.enabled) null else false), "secret" to (if (secret) true else null))
        },
    )

    fun mapToEnv(m: Map<*, *>, name: String): Environment {
        val list = (m["variables"] as? List<*>)?.mapNotNull { map(it) } ?: emptyList()
        return Environment(
            name,
            list.map { KV(s(it["name"]), s(it["value"]), it["enabled"] != false) },
            list.filter { it["secret"] == true }.map { s(it["name"]) }.toSet(),
        )
    }

    fun requestText(r: ApiRequest): String = Yml.dump(requestToMap(r))
    fun parseRequest(text: String, fallbackName: String = "Request"): ApiRequest? =
        (Yml.parse(text) as? Map<*, *>)?.takeIf { it.containsKey("url") || it.containsKey("method") }?.let { mapToRequest(it, fallbackName) }
}
