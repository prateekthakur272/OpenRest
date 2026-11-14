package dev.prateekthakur.openrest.core

/** Reader / writer for Bruno's `.bru` file format, so collections are plain git-friendly files on disk. */
object Bru {
    class Block(val name: String, val lines: List<String>, val isList: Boolean)

    private val headerRe = Regex("^([\\w:\\-]+)\\s*([{\\[])\\s*$")
    private val METHODS = setOf("get", "post", "put", "patch", "delete", "head", "options", "trace", "connect")

    fun blocks(text: String): List<Block> {
        val out = mutableListOf<Block>()
        val lines = text.replace("\r\n", "\n").split("\n")
        var i = 0
        while (i < lines.size) {
            val m = headerRe.find(lines[i])
            if (m != null) {
                val close = if (m.groupValues[2] == "{") "}" else "]"
                val body = mutableListOf<String>()
                i++
                while (i < lines.size && lines[i].trimEnd() != close) { body += lines[i]; i++ }
                out += Block(m.groupValues[1], body, close == "]")
            }
            i++
        }
        return out
    }

    private fun dict(b: Block): List<KV> = b.lines.mapNotNull { raw ->
        val l = raw.trim()
        if (l.isEmpty()) return@mapNotNull null
        val idx = l.indexOf(':')
        if (idx < 0) return@mapNotNull null
        var k = l.substring(0, idx).trim()
        val enabled = !k.startsWith("~")
        k = k.removePrefix("~")
        var v = l.substring(idx + 1).trim()
        var isFile = false
        var ct = ""
        val fm = Regex("^@file\\((.*?)\\)(?:\\s*@contentType\\((.*)\\))?$").find(v)
        if (fm != null) { isFile = true; v = fm.groupValues[1]; ct = fm.groupValues[2] }
        KV(k, v, enabled, isFile, ct)
    }

    private fun plain(b: Block): Map<String, String> = dict(b).associate { it.name to it.value }

    private fun text(b: Block): String = b.lines.joinToString("\n") { if (it.startsWith("  ")) it.substring(2) else it.trimStart() }.trimEnd()

    private fun authFrom(blocks: List<Block>, modeStr: String?): Auth {
        var a = Auth(AuthMode.fromBru(modeStr ?: "none"))
        for (b in blocks.filter { it.name.startsWith("auth:") }) {
            val m = plain(b)
            when (b.name.removePrefix("auth:")) {
                "basic", "digest" -> a = a.copy(username = m["username"] ?: "", password = m["password"] ?: "")
                "bearer" -> a = a.copy(token = m["token"] ?: "")
                "apikey" -> a = a.copy(keyName = m["key"] ?: "", keyValue = m["value"] ?: "", placement = m["placement"] ?: "header")
                "oauth2" -> a = a.copy(
                    grantType = m["grant_type"] ?: "client_credentials",
                    tokenUrl = m["access_token_url"] ?: "",
                    clientId = m["client_id"] ?: "",
                    clientSecret = m["client_secret"] ?: "",
                    scope = m["scope"] ?: "",
                    username = m["username"] ?: "",
                    password = m["password"] ?: "",
                )
            }
        }
        return a
    }

    fun parseRequest(src: String, fallbackName: String = "Request"): ApiRequest {
        var r = ApiRequest(name = fallbackName, auth = Auth(AuthMode.None))
        val bl = blocks(src)
        var authMode: String? = null
        for (b in bl) {
            val n = b.name
            when {
                n == "meta" -> { val m = plain(b); r = r.copy(name = m["name"] ?: fallbackName, seq = m["seq"]?.toIntOrNull() ?: 1) }
                n in METHODS -> {
                    val m = plain(b)
                    r = r.copy(method = n.uppercase(), url = m["url"] ?: "", bodyMode = BodyMode.fromBru(m["body"] ?: "none"))
                    authMode = m["auth"]
                }
                n == "params:query" -> r = r.copy(params = dict(b))
                n == "params:path" -> r = r.copy(pathParams = dict(b))
                n == "headers" -> r = r.copy(headers = dict(b))
                n == "body:json" -> r = r.copy(bodyJson = text(b))
                n == "body:text" -> r = r.copy(bodyText = text(b))
                n == "body:xml" -> r = r.copy(bodyXml = text(b))
                n == "body:graphql" -> r = r.copy(bodyGraphql = text(b))
                n == "body:graphql:vars" -> r = r.copy(bodyGraphqlVars = text(b))
                n == "body:form-urlencoded" -> r = r.copy(formUrlEncoded = dict(b))
                n == "body:multipart-form" -> r = r.copy(multipart = dict(b))
                n == "body:file" -> r = r.copy(bodyFile = dict(b).firstOrNull { it.enabled }?.value ?: "")
                n == "vars:pre-request" -> r = r.copy(preVars = dict(b))
                n == "vars:post-response" -> r = r.copy(postVars = dict(b))
                n == "assert" -> r = r.copy(assertions = dict(b))
                n == "script:pre-request" -> r = r.copy(preScript = text(b))
                n == "script:post-response" -> r = r.copy(postScript = text(b))
                n == "tests" -> r = r.copy(tests = text(b))
                n == "docs" -> r = r.copy(docs = text(b))
                n == "settings" -> { val m = plain(b); r = r.copy(encodeUrl = m["encodeUrl"]?.toBooleanStrictOrNull() ?: true, timeoutMs = m["timeout"]?.toIntOrNull() ?: 0) }
            }
        }
        return r.copy(auth = authFrom(bl, authMode))
    }

    fun parseConfig(src: String, fallbackName: String = ""): NodeConfig {
        var c = NodeConfig(name = fallbackName)
        val bl = blocks(src)
        var authMode: String? = null
        for (b in bl) when (b.name) {
            "meta" -> { val m = plain(b); c = c.copy(name = m["name"] ?: fallbackName, seq = m["seq"]?.toIntOrNull() ?: 1) }
            "headers" -> c = c.copy(headers = dict(b))
            "auth" -> authMode = plain(b)["mode"]
            "vars:pre-request" -> c = c.copy(preVars = dict(b))
            "vars:post-response" -> c = c.copy(postVars = dict(b))
            "script:pre-request" -> c = c.copy(preScript = text(b))
            "script:post-response" -> c = c.copy(postScript = text(b))
            "tests" -> c = c.copy(tests = text(b))
            "docs" -> c = c.copy(docs = text(b))
        }
        return c.copy(auth = authFrom(bl, authMode))
    }

    fun parseEnv(src: String, name: String): Environment {
        val bl = blocks(src)
        val vars = bl.firstOrNull { it.name == "vars" && !it.isList }?.let { dict(it) } ?: emptyList()
        val secrets = bl.firstOrNull { it.name == "vars:secret" }?.lines?.map { it.trim().trimEnd(',').trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
        return Environment(name, vars, secrets)
    }

    // ---------- writers ----------

    private fun StringBuilder.dictBlock(name: String, items: List<KV>) {
        if (items.isEmpty()) return
        append(name).append(" {\n")
        for (kv in items) {
            append("  ").append(if (kv.enabled) "" else "~").append(kv.name).append(": ")
            if (kv.isFile) {
                append("@file(").append(kv.value).append(")")
                if (kv.contentType.isNotEmpty()) append(" @contentType(").append(kv.contentType).append(")")
            } else append(kv.value.replace("\n", " "))
            append('\n')
        }
        append("}\n\n")
    }

    private fun StringBuilder.textBlock(name: String, body: String) {
        if (body.isBlank()) return
        append(name).append(" {\n")
        for (l in body.replace("\r\n", "\n").split("\n")) append("  ").append(l).append('\n')
        append("}\n\n")
    }

    private fun StringBuilder.authBlocks(a: Auth) {
        when (a.mode) {
            AuthMode.Basic -> dictBlock("auth:basic", listOf(KV("username", a.username), KV("password", a.password)))
            AuthMode.Digest -> dictBlock("auth:digest", listOf(KV("username", a.username), KV("password", a.password)))
            AuthMode.Bearer -> dictBlock("auth:bearer", listOf(KV("token", a.token)))
            AuthMode.ApiKey -> dictBlock("auth:apikey", listOf(KV("key", a.keyName), KV("value", a.keyValue), KV("placement", a.placement)))
            AuthMode.OAuth2 -> dictBlock(
                "auth:oauth2",
                listOf(
                    KV("grant_type", a.grantType), KV("access_token_url", a.tokenUrl), KV("client_id", a.clientId),
                    KV("client_secret", a.clientSecret), KV("scope", a.scope), KV("username", a.username), KV("password", a.password),
                ),
            )
            else -> {}
        }
    }

    fun serializeRequest(r: ApiRequest): String = buildString {
        dictBlock("meta", listOf(KV("name", r.name), KV("type", "http"), KV("seq", r.seq.toString())))
        dictBlock(r.method.lowercase(), listOf(KV("url", r.url), KV("body", r.bodyMode.bru), KV("auth", r.auth.mode.bru)))
        dictBlock("params:query", r.params)
        dictBlock("params:path", r.pathParams)
        dictBlock("headers", r.headers)
        authBlocks(r.auth)
        textBlock("body:json", r.bodyJson)
        textBlock("body:text", r.bodyText)
        textBlock("body:xml", r.bodyXml)
        textBlock("body:graphql", r.bodyGraphql)
        textBlock("body:graphql:vars", r.bodyGraphqlVars)
        dictBlock("body:form-urlencoded", r.formUrlEncoded)
        dictBlock("body:multipart-form", r.multipart)
        if (r.bodyFile.isNotBlank()) dictBlock("body:file", listOf(KV("file", r.bodyFile, isFile = true)))
        dictBlock("vars:pre-request", r.preVars)
        dictBlock("vars:post-response", r.postVars)
        dictBlock("assert", r.assertions)
        textBlock("script:pre-request", r.preScript)
        textBlock("script:post-response", r.postScript)
        textBlock("tests", r.tests)
        textBlock("docs", r.docs)
        dictBlock("settings", listOf(KV("encodeUrl", r.encodeUrl.toString())) + if (r.timeoutMs > 0) listOf(KV("timeout", r.timeoutMs.toString())) else emptyList())
    }.trimEnd() + "\n"

    fun serializeConfig(c: NodeConfig, withMeta: Boolean): String = buildString {
        if (withMeta) dictBlock("meta", listOf(KV("name", c.name), KV("seq", c.seq.toString())))
        dictBlock("headers", c.headers)
        if (c.auth.mode != AuthMode.None) dictBlock("auth", listOf(KV("mode", c.auth.mode.bru)))
        authBlocks(c.auth)
        dictBlock("vars:pre-request", c.preVars)
        dictBlock("vars:post-response", c.postVars)
        textBlock("script:pre-request", c.preScript)
        textBlock("script:post-response", c.postScript)
        textBlock("tests", c.tests)
        textBlock("docs", c.docs)
    }.trimEnd().let { if (it.isEmpty()) "" else it + "\n" }

    fun serializeEnv(e: Environment): String = buildString {
        dictBlock("vars", e.vars.filter { it.name !in e.secrets })
        if (e.secrets.isNotEmpty()) {
            append("vars:secret [\n")
            append(e.secrets.joinToString(",\n") { "  $it" })
            append("\n]\n\n")
        }
    }.trimEnd().let { if (it.isEmpty()) "vars {\n}\n" else it + "\n" }
}
