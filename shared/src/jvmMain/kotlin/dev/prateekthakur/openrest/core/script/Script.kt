package dev.prateekthakur.openrest.core.script

import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.utils.*

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.PolyglotException
import java.util.Base64

/** Mutable view of the request that scripts can read/modify via `req`. */
class ReqView(
    var name: String,
    var url: String,
    var method: String,
    var headers: LinkedHashMap<String, String>,
    var body: String,
    var timeout: Int,
    var authMode: String,
)

/** Bridge object exposed to JavaScript as `__host`. Only primitive/JSON-string values cross the boundary. */
@Suppress("unused")
class ScriptHost(
    val runtime: MutableMap<String, Any?>,
    val envVars: MutableMap<String, String>,
    val collVars: Map<String, String>,
    val processEnv: Map<String, String>,
    val envName: String,
    val collName: String,
    val workDir: String,
    val interpolator: (String) -> String,
) {
    var req: ReqView? = null
    var resultJson: String = "null"
    val tests = mutableListOf<TestResult>()
    val logs = mutableListOf<String>()
    var next: String? = null
    var skipped = false
    var stopped = false
    var envChanged = false

    fun reqJson(): String {
        val r = req ?: return "{}"
        return Json.stringify(
            linkedMapOf(
                "name" to r.name, "url" to r.url, "method" to r.method,
                "headers" to r.headers, "body" to r.body, "timeout" to r.timeout.toLong(), "authMode" to r.authMode,
            ),
        )
    }

    fun applyReq(json: String) {
        val r = req ?: return
        val m = Json.obj(Json.parse(json)) ?: return
        r.url = m["url"] as? String ?: r.url
        r.method = m["method"] as? String ?: r.method
        r.body = Json.str(m["body"]) ?: ""
        r.timeout = (m["timeout"] as? Number)?.toInt() ?: r.timeout
        Json.obj(m["headers"])?.let { h -> r.headers = LinkedHashMap(h.mapValues { it.value.toString() }) }
    }

    fun resJson(): String = resultJson
    fun getVar(n: String): String? = if (runtime.containsKey(n)) Json.stringify(runtime[n]) else null
    fun setVar(n: String, json: String?) { runtime[n] = if (json == null) null else Json.tryParse(json) }
    fun deleteVar(n: String) { runtime.remove(n) }
    fun deleteAllVars() { runtime.clear() }
    fun getEnvVar(n: String): String? = envVars[n]?.let { Json.quote(it) }
    fun setEnvVar(n: String, json: String?) { envVars[n] = if (json == null) "" else Json.str(Json.tryParse(json)) ?: ""; envChanged = true }
    fun getCollVar(n: String): String? = collVars[n]?.let { Json.quote(it) }
    fun getProcessEnv(n: String): String? = processEnv[n] ?: System.getenv(n)
    fun envName(): String = envName
    fun collectionName(): String = collName
    fun cwd(): String = workDir
    fun interpolate(s: String): String = interpolator(s)
    fun sleep(ms: Double) { Thread.sleep(ms.toLong().coerceIn(0, 60_000)) }
    fun nextReq(n: String?) { next = n }
    fun skip() { skipped = true }
    fun stop() { stopped = true }
    fun log(level: String, msg: String) { logs += if (level == "log") msg else "[$level] $msg" }
    fun testResult(name: String, pass: Boolean, msg: String) { tests += TestResult(name, pass, msg) }
    fun btoa(s: String): String = Base64.getEncoder().encodeToString(s.toByteArray(Charsets.ISO_8859_1))
    fun atob(s: String): String = String(Base64.getDecoder().decode(s), Charsets.ISO_8859_1)
    var lastError: String? = null
    fun error(msg: String) { lastError = msg }
}

class ScriptEngine(val host: ScriptHost) : AutoCloseable {
    private val ctx: Context = Context.newBuilder("js")
        .engine(shared)
        .allowHostAccess(HostAccess.ALL)
        .build()

    init {
        ctx.getBindings("js").putMember("__host", host)
        val prelude = ScriptEngine::class.java.getResourceAsStream("/prelude.js")!!.bufferedReader().readText()
        ctx.eval("js", prelude)
    }

    fun setRequest(v: ReqView) {
        host.req = v
        ctx.eval("js", "__loadReq()")
    }

    fun setResponse(r: HttpResult?) {
        host.resultJson = if (r == null || r.error != null) "null" else Json.stringify(
            linkedMapOf(
                "status" to r.status.toLong(), "statusText" to r.statusText,
                "headers" to r.headers.map { listOf(it.first, it.second) },
                "body" to r.text, "time" to r.timeMs, "size" to r.size.toLong(), "url" to r.finalUrl,
            ),
        )
        ctx.eval("js", "__loadRes()")
    }

    /** Runs a user script. Returns an error message on failure, null otherwise. */
    fun run(code: String, syncRequest: Boolean = false): String? {
        if (code.isBlank()) return null
        host.lastError = null
        return try {
            ctx.eval("js", "(async function(){ try {\n$code\n} catch(e) { __host.error(String(e && e.stack ? e.stack : e)); } })();")
            if (syncRequest) ctx.eval("js", "__saveReq()")
            host.lastError
        } catch (e: PolyglotException) {
            e.message ?: "Script error"
        }
    }

    fun assertOne(expr: String, spec: String): Pair<Boolean, String> = try {
        val f = ctx.getBindings("js").getMember("__assert")
        val r = Json.obj(Json.parse(f.execute(expr, spec).asString()))!!
        (r["pass"] as? Boolean ?: false) to (r["msg"] as? String ?: "")
    } catch (e: Exception) {
        false to (e.message ?: "assertion error")
    }

    /** Evaluate a JS expression (used by post-response vars). Returns the result as a string, or null. */
    fun evalExpr(expr: String): String? = try {
        val f = ctx.getBindings("js").getMember("__evalExpr")
        val v = f.execute(expr)
        if (v.isNull) null else Json.str(Json.tryParse(v.asString()))
    } catch (e: Exception) { null }

    override fun close() { runCatching { ctx.close(true) } }

    companion object {
        private val shared: org.graalvm.polyglot.Engine by lazy {
            org.graalvm.polyglot.Engine.newBuilder().option("engine.WarnInterpreterOnly", "false").build()
        }
    }
}
