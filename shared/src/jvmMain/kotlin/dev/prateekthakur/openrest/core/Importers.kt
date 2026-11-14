package dev.prateekthakur.openrest.core

import java.nio.file.Path
import kotlin.io.path.*

object Importers {

    // ---------------- cURL ----------------

    fun tokenize(cmd: String): List<String> {
        val out = mutableListOf<String>()
        val s = cmd.replace("\\\r\n", " ").replace("\\\n", " ")
        val cur = StringBuilder()
        var inTok = false
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '\'' -> {
                    inTok = true; i++
                    while (i < s.length && s[i] != '\'') cur.append(s[i++])
                }
                c == '"' -> {
                    inTok = true; i++
                    while (i < s.length && s[i] != '"') {
                        if (s[i] == '\\' && i + 1 < s.length && s[i + 1] in "\"\\$`") { cur.append(s[i + 1]); i += 2 } else cur.append(s[i++])
                    }
                }
                c == '$' && i + 1 < s.length && s[i + 1] == '\'' -> {
                    inTok = true; i += 2
                    while (i < s.length && s[i] != '\'') {
                        if (s[i] == '\\' && i + 1 < s.length) {
                            i++
                            when (s[i]) { 'n' -> cur.append('\n'); 't' -> cur.append('\t'); 'r' -> cur.append('\r'); else -> cur.append(s[i]) }
                            i++
                        } else cur.append(s[i++])
                    }
                }
                c == '\\' && i + 1 < s.length -> { inTok = true; cur.append(s[++i]) }
                c.isWhitespace() -> { if (inTok) { out += cur.toString(); cur.clear(); inTok = false } }
                else -> { inTok = true; cur.append(c) }
            }
            i++
        }
        if (inTok) out += cur.toString()
        return out
    }

    fun parseCurl(cmd: String): ApiRequest? {
        val t = tokenize(cmd.trim()).toMutableList()
        if (t.isEmpty() || t[0] != "curl") return null
        var method: String? = null
        var url = ""
        val headers = mutableListOf<KV>()
        val data = mutableListOf<String>()
        val forms = mutableListOf<KV>()
        var auth = Auth(AuthMode.Inherit)
        var getMode = false
        var i = 1
        fun next() = t.getOrElse(++i) { "" }
        while (i < t.size) {
            val a = t[i]
            when {
                a == "-X" || a == "--request" -> method = next().uppercase()
                a == "-H" || a == "--header" -> { val h = next(); val idx = h.indexOf(':'); if (idx > 0) headers += KV(h.substring(0, idx).trim(), h.substring(idx + 1).trim()) }
                a in setOf("-d", "--data", "--data-raw", "--data-binary", "--data-ascii", "--data-urlencode") -> data += next()
                a == "-F" || a == "--form" -> { val f = next(); val idx = f.indexOf('='); if (idx > 0) { val v = f.substring(idx + 1); forms += if (v.startsWith("@")) KV(f.substring(0, idx), v.substring(1), isFile = true) else KV(f.substring(0, idx), v) } }
                a == "-u" || a == "--user" -> { val u = next(); auth = Auth(AuthMode.Basic, username = u.substringBefore(':'), password = u.substringAfter(':', "")) }
                a == "-b" || a == "--cookie" -> headers += KV("Cookie", next())
                a == "-A" || a == "--user-agent" -> headers += KV("User-Agent", next())
                a == "-e" || a == "--referer" -> headers += KV("Referer", next())
                a == "--url" -> url = next()
                a == "-G" || a == "--get" -> getMode = true
                a in setOf("-o", "--output", "-m", "--max-time", "--connect-timeout", "-x", "--proxy", "--retry", "-w", "--write-out", "-c", "--cookie-jar", "--cacert", "--cert", "--key") -> next()
                a.startsWith("-") -> {}
                else -> if (url.isEmpty()) url = a
            }
            i++
        }
        if (url.isEmpty()) return null
        val ct = headers.firstOrNull { it.name.equals("content-type", true) }?.value?.lowercase() ?: ""
        var r = ApiRequest(name = runCatching { java.net.URI(url).path.trimEnd('/').substringAfterLast('/').ifEmpty { java.net.URI(url).host } }.getOrDefault("Imported"), auth = auth)
        val joined = data.joinToString("&")
        r = when {
            forms.isNotEmpty() -> r.copy(bodyMode = BodyMode.Multipart, multipart = forms)
            data.isNotEmpty() && !getMode -> when {
                ct.contains("json") || joined.trim().let { it.startsWith("{") || it.startsWith("[") } -> r.copy(bodyMode = BodyMode.Json, bodyJson = Json.prettyText(joined) ?: joined)
                ct.contains("x-www-form-urlencoded") || ct.isEmpty() -> r.copy(
                    bodyMode = BodyMode.Form,
                    formUrlEncoded = joined.split("&").filter { it.isNotEmpty() }.map {
                        KV(java.net.URLDecoder.decode(it.substringBefore('='), Charsets.UTF_8), java.net.URLDecoder.decode(it.substringAfter('=', ""), Charsets.UTF_8))
                    },
                )
                ct.contains("xml") -> r.copy(bodyMode = BodyMode.Xml, bodyXml = joined)
                else -> r.copy(bodyMode = BodyMode.Text, bodyText = joined)
            }
            else -> r
        }
        if (getMode && data.isNotEmpty()) url += (if (url.contains('?')) "&" else "?") + joined
        val finalMethod = method ?: if (forms.isNotEmpty() || (data.isNotEmpty() && !getMode)) "POST" else "GET"
        val hs = headers.filterNot { it.name.equals("content-type", true) && r.bodyMode in setOf(BodyMode.Multipart) }
        return r.copy(method = finalMethod, url = url, headers = hs, params = UrlSync.paramsFromUrl(url, emptyList()))
    }

    // ---------------- writing helper ----------------

    internal class Writer(val root: Path) {
        fun folder(parent: Path, name: String, seq: Int, cfg: NodeConfig = NodeConfig()): Path {
            val d = parent.resolve(Collections.sanitize(name)).let { var p = it; var n = 1; while (p.exists()) p = parent.resolve(Collections.sanitize(name) + " (${n++})"); p }
            d.createDirectories()
            Collections.saveFolderConfig(d, cfg.copy(name = name, seq = seq))
            return d
        }
        fun request(dir: Path, r: ApiRequest, seq: Int) {
            Collections.writeRequest(Collections.uniqueFile(dir, r.name), r.copy(seq = seq, params = if (r.params.isEmpty()) UrlSync.paramsFromUrl(r.url, emptyList()) else r.params))
        }
    }

    internal fun newCollectionDir(parent: Path, name: String): Path {
        var n = 0
        while (true) {
            val nm = if (n == 0) name else "$name ($n)"
            val d = parent.resolve(Collections.sanitize(nm))
            if (!d.exists()) return Collections.create(parent, nm)
            n++
        }
    }

    // ---------------- Postman ----------------

    private fun translatePm(src: String): String = src
        .replace("pm.environment.set(", "bru.setEnvVar(").replace("pm.environment.get(", "bru.getEnvVar(")
        .replace("pm.variables.set(", "bru.setVar(").replace("pm.variables.get(", "bru.getVar(")
        .replace("pm.collectionVariables.set(", "bru.setVar(").replace("pm.collectionVariables.get(", "bru.getVar(")
        .replace("pm.globals.set(", "bru.setVar(").replace("pm.globals.get(", "bru.getVar(")
        .replace("pm.test(", "test(").replace("pm.expect(", "expect(")
        .replace("pm.response.json()", "res.getBody()").replace("pm.response.code", "res.status")
        .replace("pm.response.status", "res.statusText").replace("pm.response.responseTime", "res.responseTime")
        .replace("pm.response.text()", "JSON.stringify(res.getBody())")
        .replace("pm.request.url", "req.getUrl()")

    private fun pmScripts(events: List<Any?>?): Triple<String, String, Unit> {
        var pre = ""; var test = ""
        events?.mapNotNull { Json.obj(it) }?.forEach { e ->
            val code = Json.arr(Json.obj(e["script"])?.get("exec"))?.joinToString("\n") { it.toString() } ?: ""
            if (e["listen"] == "prerequest") pre = translatePm(code) else if (e["listen"] == "test") test = translatePm(code)
        }
        return Triple(pre, test, Unit)
    }

    private fun pmKvs(l: Any?, keyName: String = "key"): List<KV> =
        Json.arr(l)?.mapNotNull { Json.obj(it) }?.map { KV(it[keyName]?.toString() ?: "", it["value"]?.toString() ?: "", it["disabled"] != true) } ?: emptyList()

    private fun pmAuth(a: Map<String, Any?>?): Auth? {
        a ?: return null
        val type = a["type"] as? String ?: return null
        val m = (Json.arr(a[type])?.mapNotNull { Json.obj(it) } ?: emptyList()).associate { it["key"].toString() to (it["value"]?.toString() ?: "") }
        return when (type) {
            "bearer" -> Auth(AuthMode.Bearer, token = m["token"] ?: "")
            "basic" -> Auth(AuthMode.Basic, username = m["username"] ?: "", password = m["password"] ?: "")
            "digest" -> Auth(AuthMode.Digest, username = m["username"] ?: "", password = m["password"] ?: "")
            "apikey" -> Auth(AuthMode.ApiKey, keyName = m["key"] ?: "", keyValue = m["value"] ?: "", placement = if (m["in"] == "query") "queryparams" else "header")
            "noauth" -> Auth(AuthMode.None)
            "oauth2" -> Auth(AuthMode.OAuth2, grantType = "client_credentials", tokenUrl = m["accessTokenUrl"] ?: "", clientId = m["clientId"] ?: "", clientSecret = m["clientSecret"] ?: "", scope = m["scope"] ?: "")
            else -> null
        }
    }

    private fun pmRequest(item: Map<String, Any?>): ApiRequest {
        val reqAny = item["request"]
        val name = item["name"]?.toString() ?: "Request"
        if (reqAny is String) return ApiRequest(name = name, url = reqAny)
        val rq = Json.obj(reqAny) ?: return ApiRequest(name = name)
        val urlAny = rq["url"]
        val url = when (urlAny) { is String -> urlAny; is Map<*, *> -> urlAny["raw"]?.toString() ?: ""; else -> "" }
        var r = ApiRequest(
            name = name, method = (rq["method"] as? String ?: "GET").uppercase(), url = url,
            headers = pmKvs(rq["header"]),
            auth = pmAuth(Json.obj(rq["auth"])) ?: Auth(AuthMode.Inherit),
        )
        Json.obj(urlAny)?.let { u ->
            r = r.copy(params = pmKvs(u["query"]), pathParams = pmKvs(u["variable"]))
        }
        val body = Json.obj(rq["body"])
        if (body != null) {
            when (body["mode"]) {
                "raw" -> {
                    val raw = body["raw"]?.toString() ?: ""
                    val lang = Json.obj(Json.obj(body["options"])?.get("raw"))?.get("language")?.toString()
                    r = if (lang == "json" || r.headers.any { it.name.equals("content-type", true) && it.value.contains("json") } || raw.trim().startsWith("{")) r.copy(bodyMode = BodyMode.Json, bodyJson = raw)
                    else if (lang == "xml") r.copy(bodyMode = BodyMode.Xml, bodyXml = raw)
                    else r.copy(bodyMode = BodyMode.Text, bodyText = raw)
                }
                "urlencoded" -> r = r.copy(bodyMode = BodyMode.Form, formUrlEncoded = pmKvs(body["urlencoded"]))
                "formdata" -> r = r.copy(
                    bodyMode = BodyMode.Multipart,
                    multipart = Json.arr(body["formdata"])?.mapNotNull { Json.obj(it) }?.map {
                        if (it["type"] == "file") KV(it["key"].toString(), it["src"]?.toString() ?: "", it["disabled"] != true, true) else KV(it["key"].toString(), it["value"]?.toString() ?: "", it["disabled"] != true)
                    } ?: emptyList(),
                )
                "graphql" -> { val g = Json.obj(body["graphql"]); r = r.copy(bodyMode = BodyMode.Graphql, bodyGraphql = g?.get("query")?.toString() ?: "", bodyGraphqlVars = g?.get("variables")?.toString() ?: "") }
                "file" -> r = r.copy(bodyMode = BodyMode.File, bodyFile = Json.obj(body["file"])?.get("src")?.toString() ?: "")
            }
        }
        val (pre, test, _) = pmScripts(Json.arr(item["event"]))
        return r.copy(preScript = pre, tests = test, docs = rq["description"]?.let { if (it is Map<*, *>) it["content"]?.toString() else it.toString() } ?: "")
    }

    fun importPostman(text: String, parent: Path): Path {
        val root = Json.obj(Json.parse(text)) ?: error("Invalid Postman file")
        val info = Json.obj(root["info"]) ?: error("Not a Postman collection (missing 'info')")
        val name = info["name"]?.toString() ?: "Postman Collection"
        val dir = newCollectionDir(parent, name)
        val w = Writer(dir)
        val (pre, test, _) = pmScripts(Json.arr(root["event"]))
        val cfg = NodeConfig(
            name = name, auth = pmAuth(Json.obj(root["auth"])) ?: Auth(AuthMode.None),
            preVars = pmKvs(root["variable"]), preScript = pre, tests = test,
            docs = (Json.obj(info) ?: emptyMap()).let { (it["description"] as? String) ?: "" },
        )
        Collections.saveCollectionConfig(dir, cfg)
        fun walk(items: List<Any?>?, into: Path) {
            var seq = 1
            items?.mapNotNull { Json.obj(it) }?.forEach { it0 ->
                if (it0.containsKey("item")) {
                    val (fp, ft, _) = pmScripts(Json.arr(it0["event"]))
                    val fcfg = NodeConfig(headers = emptyList(), auth = pmAuth(Json.obj(it0["auth"])) ?: Auth(AuthMode.None), preScript = fp, tests = ft)
                    val d = w.folder(into, it0["name"]?.toString() ?: "Folder", seq++, fcfg)
                    walk(Json.arr(it0["item"]), d)
                } else w.request(into, pmRequest(it0), seq++)
            }
        }
        walk(Json.arr(root["item"]), dir)
        return dir
    }

    fun importPostmanEnv(text: String): Environment? {
        val m = Json.obj(Json.tryParse(text)) ?: return null
        val vals = Json.arr(m["values"]) ?: return null
        return Environment(
            m["name"]?.toString() ?: "Imported",
            vals.mapNotNull { Json.obj(it) }.map { KV(it["key"].toString(), it["value"]?.toString() ?: "", it["enabled"] != false) },
        )
    }

    // ---------------- Insomnia ----------------

    fun importInsomnia(text: String, parent: Path): Path {
        val root = Json.obj(Json.parse(text)) ?: error("Invalid Insomnia export")
        val res = Json.arr(root["resources"])?.mapNotNull { Json.obj(it) } ?: error("Not an Insomnia export")
        val ws = res.firstOrNull { it["_type"] == "workspace" }
        val name = ws?.get("name")?.toString() ?: "Insomnia Import"
        val dir = newCollectionDir(parent, name)
        val w = Writer(dir)
        fun children(pid: String?) = res.filter { it["parentId"] == pid }
        fun walk(pid: String?, into: Path) {
            var seq = 1
            children(pid).forEach { r ->
                when (r["_type"]) {
                    "request_group" -> walk(r["_id"].toString(), w.folder(into, r["name"]?.toString() ?: "Folder", seq++))
                    "request" -> {
                        val body = Json.obj(r["body"])
                        val mime = body?.get("mimeType")?.toString() ?: ""
                        val txt = body?.get("text")?.toString() ?: ""
                        var rq = ApiRequest(
                            name = r["name"]?.toString() ?: "Request", method = (r["method"]?.toString() ?: "GET").uppercase(), url = r["url"]?.toString() ?: "",
                            headers = Json.arr(r["headers"])?.mapNotNull { Json.obj(it) }?.map { KV(it["name"].toString(), it["value"]?.toString() ?: "", it["disabled"] != true) } ?: emptyList(),
                            docs = r["description"]?.toString() ?: "",
                        )
                        rq = when {
                            mime.contains("json") -> rq.copy(bodyMode = BodyMode.Json, bodyJson = txt)
                            mime.contains("x-www-form-urlencoded") -> rq.copy(bodyMode = BodyMode.Form, formUrlEncoded = pmKvs(body?.get("params"), "name"))
                            mime.contains("multipart") -> rq.copy(bodyMode = BodyMode.Multipart, multipart = pmKvs(body?.get("params"), "name"))
                            mime.contains("xml") -> rq.copy(bodyMode = BodyMode.Xml, bodyXml = txt)
                            mime.contains("graphql") -> rq.copy(bodyMode = BodyMode.Graphql, bodyGraphql = Json.obj(Json.tryParse(txt))?.get("query")?.toString() ?: txt)
                            txt.isNotEmpty() -> rq.copy(bodyMode = BodyMode.Text, bodyText = txt)
                            else -> rq
                        }
                        val au = Json.obj(r["authentication"])
                        rq = when (au?.get("type")) {
                            "bearer" -> rq.copy(auth = Auth(AuthMode.Bearer, token = au["token"]?.toString() ?: ""))
                            "basic" -> rq.copy(auth = Auth(AuthMode.Basic, username = au["username"]?.toString() ?: "", password = au["password"]?.toString() ?: ""))
                            else -> rq
                        }
                        w.request(into, rq, seq++)
                    }
                }
            }
        }
        walk(ws?.get("_id")?.toString(), dir)
        res.filter { it["_type"] == "environment" && Json.obj(it["data"]) != null }.forEach { e ->
            val vars = Json.obj(e["data"])!!.map { KV(it.key, Json.str(it.value) ?: "") }
            if (vars.isNotEmpty()) Collections.saveEnvironment(dir, Environment(e["name"]?.toString() ?: "Environment", vars))
        }
        return dir
    }

    // ---------------- OpenAPI / Swagger (JSON) ----------------

    private fun example(schema: Map<String, Any?>?, root: Map<String, Any?>, depth: Int = 0): Any? {
        schema ?: return null
        if (depth > 5) return null
        schema["\$ref"]?.toString()?.let { ref ->
            var cur: Any? = root
            ref.removePrefix("#/").split("/").forEach { cur = Json.obj(cur)?.get(it) }
            return example(Json.obj(cur), root, depth + 1)
        }
        if (schema.containsKey("example")) return schema["example"]
        if (schema.containsKey("default")) return schema["default"]
        Json.arr(schema["enum"])?.firstOrNull()?.let { return it }
        Json.arr(schema["allOf"])?.let { l -> return l.mapNotNull { example(Json.obj(it), root, depth + 1) as? Map<*, *> }.fold(linkedMapOf<String, Any?>()) { a, m -> m.forEach { (k, v) -> a[k.toString()] = v }; a } }
        Json.arr(schema["oneOf"] ?: schema["anyOf"])?.firstOrNull()?.let { return example(Json.obj(it), root, depth + 1) }
        return when (schema["type"]) {
            "object", null -> Json.obj(schema["properties"])?.let { p -> p.mapValuesTo(linkedMapOf()) { example(Json.obj(it.value), root, depth + 1) } } ?: if (schema["type"] == null) null else linkedMapOf<String, Any?>()
            "array" -> listOf(example(Json.obj(schema["items"]), root, depth + 1))
            "integer" -> 0L
            "number" -> 0.0
            "boolean" -> true
            else -> when (schema["format"]) { "date-time" -> "2024-01-01T00:00:00Z"; "date" -> "2024-01-01"; "email" -> "user@example.com"; "uuid" -> "00000000-0000-0000-0000-000000000000"; else -> "string" }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun importOpenApi(text: String, parent: Path): Path {
        val root = Json.obj(Json.tryParse(text)) ?: (Yml.tryParse(text) as? Map<String, Any?>) ?: error("Not a valid OpenAPI / Swagger document (JSON or YAML)")
        val info = Json.obj(root["info"])
        val name = info?.get("title")?.toString() ?: "OpenAPI Import"
        val dir = newCollectionDir(parent, name)
        val w = Writer(dir)
        val base = Json.arr(root["servers"])?.firstOrNull()?.let { Json.obj(it)?.get("url")?.toString() }
            ?: root["host"]?.let { (Json.arr(root["schemes"])?.firstOrNull()?.toString() ?: "https") + "://" + it + (root["basePath"]?.toString() ?: "") }
            ?: "http://localhost"
        Collections.saveEnvironment(dir, Environment("Default", listOf(KV("baseUrl", base.trimEnd('/')))))
        val paths = Json.obj(root["paths"]) ?: error("No 'paths' found")
        val folders = mutableMapOf<String, Path>()
        var seq = 1
        val order = listOf("get", "post", "put", "patch", "delete", "head", "options", "trace")
        for ((path, item0) in paths) {
            val item = Json.obj(item0) ?: continue
            val common = Json.arr(item["parameters"])?.mapNotNull { Json.obj(it) } ?: emptyList()
            for (mth in order) {
                val op = Json.obj(item[mth]) ?: continue
                val tag = Json.arr(op["tags"])?.firstOrNull()?.toString()
                val into = if (tag == null) dir else folders.getOrPut(tag) { w.folder(dir, tag, folders.size + 1) }
                val params = (common + (Json.arr(op["parameters"])?.mapNotNull { Json.obj(it) } ?: emptyList())).map { p ->
                    if (p.containsKey("\$ref")) {
                        var cur: Any? = root
                        p["\$ref"].toString().removePrefix("#/").split("/").forEach { cur = Json.obj(cur)?.get(it) }
                        Json.obj(cur) ?: p
                    } else p
                }
                fun kv(where: String) = params.filter { it["in"] == where }.map { KV(it["name"].toString(), Json.str(example(Json.obj(it["schema"]), root)) ?: "", it["required"] == true) }
                var r = ApiRequest(
                    name = op["summary"]?.toString()?.takeIf { it.isNotBlank() } ?: op["operationId"]?.toString() ?: "${mth.uppercase()} $path",
                    method = mth.uppercase(),
                    url = "{{baseUrl}}" + path.replace(Regex("\\{([^}]+)}"), ":$1"),
                    params = kv("query"), pathParams = kv("path").map { it.copy(enabled = true) }, headers = kv("header"),
                    docs = op["description"]?.toString() ?: "",
                )
                val rb = Json.obj(op["requestBody"])
                val content = Json.obj(rb?.get("content"))
                if (content != null) {
                    val (ct, media) = content.entries.firstOrNull { it.key.contains("json") }?.toPair() ?: content.entries.first().toPair()
                    val ex = example(Json.obj(Json.obj(media)?.get("schema")), root)
                    r = when {
                        ct.contains("json") -> r.copy(bodyMode = BodyMode.Json, bodyJson = Json.stringify(ex, true))
                        ct.contains("x-www-form-urlencoded") -> r.copy(bodyMode = BodyMode.Form, formUrlEncoded = (ex as? Map<*, *>)?.map { KV(it.key.toString(), Json.str(it.value) ?: "") } ?: emptyList())
                        ct.contains("multipart") -> r.copy(bodyMode = BodyMode.Multipart, multipart = (ex as? Map<*, *>)?.map { KV(it.key.toString(), Json.str(it.value) ?: "") } ?: emptyList())
                        ct.contains("xml") -> r.copy(bodyMode = BodyMode.Xml)
                        else -> r.copy(bodyMode = BodyMode.Text, bodyText = Json.str(ex) ?: "")
                    }
                }
                w.request(into, r.copy(params = r.params), seq++)
            }
        }
        return dir
    }

    // ---------------- Bruno folder -> OpenRest ----------------

    fun importBruno(src: Path, parent: Path): Path {
        val meta = runCatching { Json.obj(Json.parse(src.resolve("bruno.json").readText())) }.getOrNull()
        val name = meta?.get("name") as? String ?: src.name
        val dir = newCollectionDir(parent, name)
        val w = Writer(dir)
        val cf = src.resolve("collection.bru")
        Collections.saveCollectionConfig(dir, (if (cf.exists()) Bru.parseConfig(cf.readText(), name) else NodeConfig()).copy(name = name))
        fun walk(from: Path, into: Path) {
            val entries = from.listDirectoryEntries().sortedBy { it.name }
            var fseq = 1
            for (e in entries) {
                if (e.name.startsWith(".")) continue
                if (e.isDirectory()) {
                    if (e.name == "node_modules" || e.name == "environments" && from == src) continue
                    val fb = e.resolve("folder.bru")
                    val cfg = if (fb.exists()) Bru.parseConfig(fb.readText(), e.name) else NodeConfig(name = e.name)
                    walk(e, w.folder(into, cfg.name.ifEmpty { e.name }, if (cfg.seq == Int.MAX_VALUE || cfg.seq == 0) fseq++ else cfg.seq, cfg))
                } else if (e.extension == "bru" && e.name != "collection.bru" && e.name != "folder.bru") {
                    runCatching { Bru.parseRequest(e.readText(), e.nameWithoutExtension) }.getOrNull()?.let { w.request(into, it, it.seq) }
                }
            }
        }
        walk(src, dir)
        val envDir = src.resolve("environments")
        if (envDir.isDirectory()) envDir.listDirectoryEntries("*.bru").forEach { Collections.saveEnvironment(dir, Bru.parseEnv(it.readText(), it.nameWithoutExtension)) }
        return dir
    }

    // ---------------- OpenRest single-file bundle ----------------

    fun importBundle(text: String, parent: Path): Path {
        val root = Yml.parse(text) as? Map<*, *> ?: error("Not an OpenRest bundle")
        require(root.containsKey("openrest")) { "Not an OpenRest bundle (missing 'openrest' key)" }
        val cfg = OrYaml.mapToConfig(root, "Imported")
        val dir = newCollectionDir(parent, cfg.name)
        val w = Writer(dir)
        Collections.saveCollectionConfig(dir, cfg)
        fun walk(items: Any?, into: Path) {
            var seq = 1
            (items as? List<*>)?.mapNotNull { it as? Map<*, *> }?.forEach { m ->
                if (m.containsKey("items")) walk(m["items"], w.folder(into, OrYaml.mapToConfig(m).name.ifEmpty { "Folder" }, seq++, OrYaml.mapToConfig(m)))
                else w.request(into, OrYaml.mapToRequest(m), seq++)
            }
        }
        walk(root["items"], dir)
        (root["environments"] as? List<*>)?.mapNotNull { it as? Map<*, *> }?.forEach { m ->
            Collections.saveEnvironment(dir, OrYaml.mapToEnv(m, m["name"]?.toString() ?: "Environment"))
        }
        return dir
    }

    // ---------------- Export (Postman v2.1) ----------------

    fun exportPostman(c: Collection): String {
        fun kvs(l: List<KV>) = l.map { linkedMapOf("key" to it.name, "value" to it.value, "disabled" to !it.enabled) }
        fun item(n: Node): Map<String, Any?> = when (n) {
            is FolderNode -> linkedMapOf("name" to n.name, "item" to n.children.map { item(it) })
            is RequestNode -> {
                val r = n.request
                val body: Map<String, Any?>? = when (r.bodyMode) {
                    BodyMode.Json -> linkedMapOf("mode" to "raw", "raw" to r.bodyJson, "options" to mapOf("raw" to mapOf("language" to "json")))
                    BodyMode.Text -> linkedMapOf("mode" to "raw", "raw" to r.bodyText)
                    BodyMode.Xml -> linkedMapOf("mode" to "raw", "raw" to r.bodyXml, "options" to mapOf("raw" to mapOf("language" to "xml")))
                    BodyMode.Form -> linkedMapOf("mode" to "urlencoded", "urlencoded" to kvs(r.formUrlEncoded))
                    BodyMode.Multipart -> linkedMapOf("mode" to "formdata", "formdata" to r.multipart.map { linkedMapOf("key" to it.name, "value" to it.value, "type" to if (it.isFile) "file" else "text", "disabled" to !it.enabled) })
                    BodyMode.Graphql -> linkedMapOf("mode" to "graphql", "graphql" to linkedMapOf("query" to r.bodyGraphql, "variables" to r.bodyGraphqlVars))
                    else -> null
                }
                val req = linkedMapOf<String, Any?>("method" to r.method, "header" to kvs(r.headers), "url" to linkedMapOf("raw" to r.url))
                if (body != null) req["body"] = body
                when (r.auth.mode) {
                    AuthMode.Bearer -> req["auth"] = mapOf("type" to "bearer", "bearer" to listOf(mapOf("key" to "token", "value" to r.auth.token, "type" to "string")))
                    AuthMode.Basic -> req["auth"] = mapOf("type" to "basic", "basic" to listOf(mapOf("key" to "username", "value" to r.auth.username), mapOf("key" to "password", "value" to r.auth.password)))
                    else -> {}
                }
                linkedMapOf("name" to r.name, "request" to req)
            }
        }
        val root = linkedMapOf<String, Any?>(
            "info" to linkedMapOf("name" to c.name, "schema" to "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"),
            "item" to c.children.map { item(it) },
            "variable" to c.config.preVars.map { mapOf("key" to it.name, "value" to it.value) },
        )
        return Json.stringify(root, true)
    }
}

/** Keeps the URL query string and the params table in sync, like Bruno does. */
object UrlSync {
    private fun dec(s: String) = runCatching { java.net.URLDecoder.decode(s, Charsets.UTF_8) }.getOrDefault(s)

    fun paramsFromUrl(url: String, old: List<KV>): List<KV> {
        val q = url.indexOf('?')
        val parsed = if (q < 0) emptyList() else url.substring(q + 1).split("&").filter { it.isNotEmpty() }.map {
            val k = it.substringBefore('=')
            val v = it.substringAfter('=', "")
            // keep {{var}} readable; decode other escapes
            KV(if (k.contains("{{")) k else dec(k), if (v.contains("{{")) v else dec(v))
        }
        return parsed + old.filter { !it.enabled }
    }

    fun urlWithParams(url: String, params: List<KV>): String {
        val base = url.substringBefore('?')
        val q = params.filter { it.enabled && it.name.isNotEmpty() }.joinToString("&") { enc(it.name) + "=" + enc(it.value) }
        return if (q.isEmpty()) base else "$base?$q"
    }

    private fun enc(s: String) = if (s.contains("{{")) s else HttpExecutor.urlEncode(s)
}
