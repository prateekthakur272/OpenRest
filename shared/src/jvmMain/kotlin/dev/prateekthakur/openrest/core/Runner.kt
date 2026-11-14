package dev.prateekthakur.openrest.core

import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

class RunCtx(
    val collection: Collection,
    val envName: String?,
    val runtime: MutableMap<String, Any?>,
    val settings: AppSettings,
    val globalEnv: Environment? = null,
    /** Resolves `s1.body.id`-style references to earlier workflow steps (null outside workflows). */
    val refs: ((String) -> String?)? = null,
    /** Resolves a single-brace `{name}` / `{name.field}` to a value a workflow step passed on; null = not one of those, leave the text alone. */
    val flow: ((String) -> String?)? = null,
)

object OAuthCache {
    val tokens = mutableMapOf<String, String>()
}

/** Resolves variables, inherited config, auth and scripts for one request and executes it. */
class Execution(private val rc: RunCtx, private val node: RequestNode) {
    private val col = rc.collection
    private val folders = col.ancestorFolders(node.path)
    private val chain: List<NodeConfig> = listOf(col.config) + folders.map { it.config }
    private val req = node.request
    private val env = col.environments.firstOrNull { it.name == rc.envName }
    val envVars: MutableMap<String, String> =
        LinkedHashMap<String, String>().also { m ->
            env?.vars?.filter { it.enabled }?.forEach { m[it.name] = it.value }
            EnvOverrides.get(col.path.toString(), rc.envName ?: "")?.let { m.putAll(it) }
        }
    /** Global environment variables: the lowest-precedence layer, shared by every collection. */
    private val globalVars: Map<String, String> =
        rc.globalEnv?.vars?.filter { it.enabled }?.associate { it.name to it.value } ?: emptyMap()
    private val collVars = col.config.preVars.filter { it.enabled }.associate { it.name to it.value }
    private val folderVars: Map<String, String> =
        folders.fold(emptyMap<String, String>()) { acc, f -> acc + f.config.preVars.filter { it.enabled }.associate { it.name to it.value } }
    private val reqVars = req.preVars.filter { it.enabled }.associate { it.name to it.value }

    private fun str(v: Any?): String = when (v) {
        null -> ""
        is String -> v
        else -> Json.stringify(v)
    }

    fun lookup(n: String): String? = when {
        rc.refs != null && WorkflowRunner.isStepRef(n) -> rc.refs.invoke(n)
        n.startsWith("process.env.") -> col.dotEnv[n.removePrefix("process.env.")] ?: System.getenv(n.removePrefix("process.env."))
        rc.runtime.containsKey(n) -> str(rc.runtime[n])
        // `item.id` / `order.items[0].sku`: read into a structured variable (loop item, JSON set by a workflow)
        n.contains('.') && rc.runtime[n.substringBefore('.')].let { it is Map<*, *> || it is List<*> } ->
            str(JsonPath.query(rc.runtime[n.substringBefore('.')], n.substringAfter('.')))
        else -> reqVars[n] ?: folderVars[n] ?: envVars[n] ?: collVars[n] ?: globalVars[n]
    }

    private val flowRe = Regex("(?<!\\{)\\{([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_\\[\\]-]+)*)}")

    /** `{name}` first (only for names a workflow passed on, so JSON and other braces are never touched), then `{{variables}}`. */
    fun interp(s: String): String {
        val f = rc.flow
        val a = if (f != null && s.contains('{')) flowRe.replace(s) { m -> f(m.groupValues[1]) ?: m.value } else s
        return Variables.interpolate(a, ::lookup)
    }

    private fun mergedHeaders(): LinkedHashMap<String, String> {
        val m = LinkedHashMap<String, String>()
        fun put(kvs: List<KV>) = kvs.filter { it.enabled && it.name.isNotBlank() }.forEach { kv ->
            m.keys.firstOrNull { it.equals(kv.name, true) }?.let { m.remove(it) }
            m[kv.name] = kv.value
        }
        chain.forEach { put(it.headers) }
        put(req.headers)
        return m
    }

    private fun effectiveAuth(): Auth {
        if (req.auth.mode != AuthMode.Inherit) return req.auth
        for (f in folders.reversed()) if (f.config.auth.mode != AuthMode.None && f.config.auth.mode != AuthMode.Inherit) return f.config.auth
        return col.config.auth.takeIf { it.mode != AuthMode.Inherit } ?: Auth(AuthMode.None)
    }

    fun initialView(): ReqView {
        val body = when (req.bodyMode) {
            BodyMode.Json -> req.bodyJson
            BodyMode.Text -> req.bodyText
            BodyMode.Xml -> req.bodyXml
            BodyMode.Graphql -> req.bodyGraphql
            else -> ""
        }
        return ReqView(req.name, req.url, req.method, mergedHeaders(), body, req.timeoutMs, effectiveAuth().mode.bru)
    }

    private fun encodeUrl(u: String): String {
        val sb = StringBuilder()
        for (ch in u) {
            if (ch.code > 126 || ch in " \"<>\\^`{|}") sb.append(ch.toString().toByteArray().joinToString("") { "%%%02X".format(it) })
            else sb.append(ch)
        }
        return sb.toString()
    }

    fun buildUrl(raw: String): String {
        var u = interp(raw.trim())
        // path params  /users/:id
        val q = u.indexOf('?')
        var base = if (q >= 0) u.substring(0, q) else u
        val rest = if (q >= 0) u.substring(q) else ""
        for (p in req.pathParams.filter { it.enabled }) {
            base = Regex("(?<=/):" + Regex.escape(p.name) + "(?![\\w-])").replace(base, java.util.regex.Matcher.quoteReplacement(interp(p.value)))
        }
        u = base + rest
        if (!u.contains('?')) {
            val ps = req.params.filter { it.enabled && it.name.isNotBlank() }
            if (ps.isNotEmpty()) u += "?" + ps.joinToString("&") { HttpExecutor.urlEncode(interp(it.name)) + "=" + HttpExecutor.urlEncode(interp(it.value)) }
        }
        if (!u.startsWith("http://", true) && !u.startsWith("https://", true) && u.isNotBlank() && !u.contains("://")) u = "http://$u"
        return if (req.encodeUrl) encodeUrl(u) else u
    }

    private fun buildBody(view: ReqView): Body = when (req.bodyMode) {
        BodyMode.None -> Body.None
        BodyMode.Json -> Body.Raw(interp(view.body), "application/json")
        BodyMode.Text -> Body.Raw(interp(view.body), "text/plain")
        BodyMode.Xml -> Body.Raw(interp(view.body), "application/xml")
        BodyMode.Graphql -> {
            val vars = interp(req.bodyGraphqlVars).takeIf { it.isNotBlank() }?.let { Json.tryParse(it) } ?: emptyMap<String, Any?>()
            Body.Raw(Json.stringify(linkedMapOf("query" to interp(view.body), "variables" to vars)), "application/json")
        }
        BodyMode.Form -> Body.Form(req.formUrlEncoded.filter { it.enabled && it.name.isNotBlank() }.map { interp(it.name) to interp(it.value) })
        BodyMode.Multipart -> Body.Multipart(req.multipart.filter { it.enabled && it.name.isNotBlank() }.map { Part(interp(it.name), interp(it.value), it.isFile, it.contentType) })
        BodyMode.File -> Body.FileBody(interp(req.bodyFile))
    }

    /** Build the final request: interpolate everything and apply auth. */
    fun prepare(view: ReqView = initialView()): PreparedRequest {
        var url = buildUrl(view.url)
        val headers = LinkedHashMap<String, String>()
        view.headers.forEach { (k, v) -> headers[interp(k)] = interp(v) }
        val a = effectiveAuth()
        when (a.mode) {
            AuthMode.Basic -> headers["Authorization"] = "Basic " + Base64.getEncoder().encodeToString("${interp(a.username)}:${interp(a.password)}".toByteArray())
            AuthMode.Bearer -> headers["Authorization"] = "Bearer " + interp(a.token)
            AuthMode.ApiKey ->
                if (a.placement == "queryparams") url += (if (url.contains('?')) "&" else "?") + HttpExecutor.urlEncode(interp(a.keyName)) + "=" + HttpExecutor.urlEncode(interp(a.keyValue))
                else headers[interp(a.keyName)] = interp(a.keyValue)
            AuthMode.OAuth2 -> fetchOAuthToken(a)?.let { headers["Authorization"] = "Bearer $it" }
            else -> {}
        }
        val body = buildBody(view)
        return PreparedRequest(view.method.uppercase(), url, headers.map { it.key to it.value }, body, view.timeout)
    }

    private fun fetchOAuthToken(a: Auth): String? {
        val key = listOf(a.grantType, interp(a.tokenUrl), interp(a.clientId), interp(a.scope), interp(a.username)).joinToString("|")
        OAuthCache.tokens[key]?.let { return it }
        val fields = mutableListOf("grant_type" to a.grantType, "client_id" to interp(a.clientId), "client_secret" to interp(a.clientSecret))
        if (a.scope.isNotBlank()) fields += "scope" to interp(a.scope)
        if (a.grantType == "password") { fields += "username" to interp(a.username); fields += "password" to interp(a.password) }
        val res = HttpExecutor.execute(PreparedRequest("POST", interp(a.tokenUrl), listOf("Accept" to "application/json"), Body.Form(fields)), rc.settings)
        val tok = Json.obj(Json.tryParse(res.text))?.get("access_token") as? String
        if (tok != null) OAuthCache.tokens[key] = tok
        return tok
    }

    private fun digest(p: PreparedRequest, a: Auth, challenge: String): PreparedRequest {
        fun md5(s: String) = MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
        val params = Regex("(\\w+)=(?:\"([^\"]*)\"|([^,\\s]*))").findAll(challenge.substringAfter("Digest", "")).associate { it.groupValues[1] to it.groupValues[2].ifEmpty { it.groupValues[3] } }
        val uri = URI.create(p.url).let { (it.rawPath.ifEmpty { "/" }) + (it.rawQuery?.let { q -> "?$q" } ?: "") }
        val user = interp(a.username); val pass = interp(a.password)
        val realm = params["realm"] ?: ""; val nonce = params["nonce"] ?: ""
        val qop = params["qop"]?.split(",")?.map { it.trim() }?.firstOrNull { it == "auth" }
        val ha1 = md5("$user:$realm:$pass"); val ha2 = md5("${p.method}:$uri")
        val nc = "00000001"; val cnonce = ByteArray(8).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val response = if (qop != null) md5("$ha1:$nonce:$nc:$cnonce:$qop:$ha2") else md5("$ha1:$nonce:$ha2")
        val h = buildString {
            append("Digest username=\"$user\", realm=\"$realm\", nonce=\"$nonce\", uri=\"$uri\", algorithm=MD5, response=\"$response\"")
            if (qop != null) append(", qop=$qop, nc=$nc, cnonce=\"$cnonce\"")
            params["opaque"]?.let { append(", opaque=\"$it\"") }
        }
        return p.copy(headers = p.headers.filterNot { it.first.equals("authorization", true) } + ("Authorization" to h))
    }

    private fun send(p: PreparedRequest): HttpResult {
        var res = HttpExecutor.execute(p, rc.settings)
        val a = effectiveAuth()
        if (a.mode == AuthMode.Digest && res.status == 401) {
            val ch = res.headers.firstOrNull { it.first.equals("www-authenticate", true) && it.second.startsWith("Digest", true) }?.second
            if (ch != null) res = HttpExecutor.execute(digest(p, a, ch), rc.settings)
        }
        return res
    }

    fun run(): ExecResult {
        val hasLogic = listOf(req.preScript, req.postScript, req.tests).any { it.isNotBlank() } ||
            chain.any { it.preScript.isNotBlank() || it.postScript.isNotBlank() || it.tests.isNotBlank() || it.postVars.isNotEmpty() } ||
            req.assertions.isNotEmpty() || req.postVars.isNotEmpty()
        val host = ScriptHost(rc.runtime, envVars, collVars + folderVars, col.dotEnv, rc.envName ?: "", col.name, col.path.toString(), ::interp)
        val engine = if (hasLogic) ScriptEngine(host) else null
        val tests = mutableListOf<TestResult>()
        fun scriptErr(where: String, e: String?) { if (e != null) tests += TestResult("$where script error", false, e, "script") }
        try {
            val view = initialView()
            engine?.setRequest(view)
            // pre-request: collection -> folders -> request
            (chain.map { it.preScript } + req.preScript).forEachIndexed { i, code ->
                scriptErr(if (i == 0) "Collection pre-request" else if (i == chain.size) "Pre-request" else "Folder pre-request", engine?.run(code, true))
            }
            if (host.skipped) return ExecResult(skipped = true, logs = host.logs, tests = tests, nextRequest = host.next, stop = host.stopped)
            val prepared = prepare(host.req ?: view)
            val res = send(prepared)
            if (res.error != null) return ExecResult(prepared, res, tests, host.logs, res.error, nextRequest = host.next, stop = host.stopped)
            if (engine != null) {
                engine.setResponse(res)
                // post-response vars
                (chain.flatMap { it.postVars } + req.postVars).filter { it.enabled && it.name.isNotBlank() }.forEach { kv ->
                    engine.evalExpr(interp(kv.value))?.let { rc.runtime[kv.name] = it }
                }
                // post-response scripts: request -> folders(reverse) -> collection
                (listOf(req.postScript) + chain.reversed().map { it.postScript }).forEach { scriptErr("Post-response", engine.run(it)) }
                // declarative assertions
                req.assertions.filter { it.enabled && it.name.isNotBlank() }.forEach { kv ->
                    val (ok, msg) = engine.assertOne(kv.name, kv.value)
                    tests += TestResult("${kv.name}: ${kv.value}", ok, msg, "assert")
                }
                // tests
                (chain.map { it.tests } + req.tests).forEach { scriptErr("Tests", engine.run(it)) }
                tests += host.tests
            }
            return ExecResult(prepared, res, tests, host.logs, null, nextRequest = host.next, stop = host.stopped)
        } catch (e: Exception) {
            return ExecResult(error = e.message ?: e.toString(), tests = tests, logs = host.logs)
        } finally {
            engine?.close()
            // persist script-modified env vars back (in memory only)
            if (host.envChanged) EnvOverrides.set(col.path.toString(), rc.envName ?: "", envVars)
        }
    }
}

/** Env var changes made by scripts (bru.setEnvVar) live for the session. */
object EnvOverrides {
    private val map = mutableMapOf<String, Map<String, String>>()
    fun set(col: String, env: String, vars: Map<String, String>) { map["$col|$env"] = HashMap(vars) }
    fun get(col: String, env: String): Map<String, String>? = map["$col|$env"]
}

object Runner {
    data class Item(val node: RequestNode, val result: ExecResult)

    fun flatten(nodes: List<Node>): List<RequestNode> = nodes.flatMap { if (it is RequestNode) listOf(it) else flatten((it as FolderNode).children) }

    /** Runs requests sequentially; honours bru.runner.skipRequest / stopExecution / setNextRequest. */
    fun runAll(
        rc: RunCtx,
        requests: List<RequestNode>,
        stopOnFailure: Boolean,
        delayMs: Long,
        cancelled: () -> Boolean,
        onStart: (RequestNode) -> Unit,
        onResult: (Item) -> Unit,
    ) {
        var i = 0
        var guard = 0
        while (i < requests.size && guard++ < requests.size * 20 + 20) {
            if (cancelled()) return
            val node = requests[i]
            onStart(node)
            val r = Execution(rc, node).run()
            onResult(Item(node, r))
            val failed = r.error != null || r.tests.any { !it.passed }
            if (r.stop || (stopOnFailure && failed)) return
            i = if (r.nextRequest != null) {
                val idx = requests.indexOfFirst { it.name == r.nextRequest }
                if (idx >= 0) idx else requests.size
            } else i + 1
            if (delayMs > 0) Thread.sleep(delayMs)
        }
    }
}
