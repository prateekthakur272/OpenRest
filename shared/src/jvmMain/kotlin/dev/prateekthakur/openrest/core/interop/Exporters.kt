package dev.prateekthakur.openrest.core.interop

import dev.prateekthakur.openrest.core.codegen.*
import dev.prateekthakur.openrest.core.http.*
import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.model.Collection
import dev.prateekthakur.openrest.core.storage.*
import dev.prateekthakur.openrest.utils.*

import java.nio.file.Path
import kotlin.io.path.*

/** Exports a collection to other tools' formats. */
object Exporters {
    class Format(val id: String, val label: String, val ext: String, val hint: String) {
        val isDirectory get() = ext.isEmpty()
    }

    val formats = listOf(
        Format("bundle", "OpenRest Bundle (single YAML file)", ".openrest.yaml", "Whole collection and environments in one shareable file. Re-importable."),
        Format("postman", "Postman Collection v2.1", ".postman_collection.json", "Folders, requests, bodies, auth and collection variables."),
        Format("bruno", "Bruno Collection (folder of .bru files)", "", "Creates a Bruno collection folder inside the chosen directory."),
        Format("openapi-yaml", "OpenAPI 3.0 / Swagger (YAML)", ".openapi.yaml", "Paths, parameters, request bodies and security schemes."),
        Format("openapi-json", "OpenAPI 3.0 / Swagger (JSON)", ".openapi.json", "Same as above, as JSON (loads in Swagger UI / Editor)."),
        Format("insomnia", "Insomnia v4", ".insomnia.json", "Requests, folders and environments."),
        Format("http", "HTTP file (.http)", ".http", "For VS Code REST Client and JetBrains HTTP Client."),
        Format("curl", "Shell script of cURL commands", ".sh", "One curl command per request."),
    )

    /** Returns the path that was written. */
    fun export(format: String, c: Collection, dest: Path): Path = when (format) {
        "bundle" -> dest.also { it.writeText(Yml.dump(bundle(c))) }
        "postman" -> dest.also { it.writeText(Importers.exportPostman(c)) }
        "bruno" -> bruno(c, dest)
        "openapi-yaml" -> dest.also { it.writeText(Yml.dump(openApi(c))) }
        "openapi-json" -> dest.also { it.writeText(Json.stringify(openApi(c), true)) }
        "insomnia" -> dest.also { it.writeText(Json.stringify(insomnia(c), true)) }
        "http" -> dest.also { it.writeText(httpFile(c)) }
        "curl" -> dest.also { it.writeText(curlScript(c)) }
        else -> error("Unknown export format: $format")
    }

    // ---------------- bundle ----------------

    private fun nodeMap(n: Node): Map<String, Any?> = when (n) {
        is FolderNode -> LinkedHashMap(OrYaml.configToMap(n.config.copy(name = n.name), true)).also { it["items"] = n.children.map { c -> nodeMap(c) } }
        is RequestNode -> OrYaml.requestToMap(n.request)
    }

    private fun bundle(c: Collection): Map<String, Any?> {
        val m = linkedMapOf<String, Any?>("openrest" to OrYaml.VERSION.toLong())
        m.putAll(OrYaml.configToMap(c.config.copy(name = c.name), false))
        m["items"] = c.children.map { nodeMap(it) }
        if (c.environments.isNotEmpty()) m["environments"] = c.environments.map { OrYaml.envToMap(it) }
        return m
    }

    // ---------------- Bruno ----------------

    private fun bruno(c: Collection, destParent: Path): Path {
        val root = destParent.resolve(Collections.sanitize(c.name)).let { var p = it; var i = 1; while (p.exists()) p = destParent.resolve("${Collections.sanitize(c.name)} (${i++})"); p }
        root.createDirectories()
        root.resolve("bruno.json").writeText(Json.stringify(linkedMapOf("version" to "1", "name" to c.name, "type" to "collection", "ignore" to listOf("node_modules", ".git")), true))
        Bru.serializeConfig(c.config, false).takeIf { it.isNotBlank() }?.let { root.resolve("collection.bru").writeText(it) }
        fun walk(nodes: List<Node>, dir: Path) {
            nodes.forEachIndexed { i, n ->
                when (n) {
                    is FolderNode -> {
                        val d = dir.resolve(Collections.sanitize(n.name)).also { it.createDirectories() }
                        d.resolve("folder.bru").writeText(Bru.serializeConfig(n.config.copy(name = n.name, seq = i + 1), true))
                        walk(n.children, d)
                    }
                    is RequestNode -> Collections.uniqueFile(dir, n.name, ".bru").writeText(Bru.serializeRequest(n.request.copy(seq = i + 1, params = n.request.params)))
                }
            }
        }
        walk(c.children, root)
        if (c.environments.isNotEmpty()) {
            val ed = root.resolve("environments").also { it.createDirectories() }
            c.environments.forEach { ed.resolve(Collections.sanitize(it.name) + ".bru").writeText(Bru.serializeEnv(it)) }
        }
        return root
    }

    // ---------------- raw (un-interpolated) request view ----------------

    private fun raw(r: ApiRequest): PreparedRequest {
        val h = r.headers.filter { it.enabled && it.name.isNotBlank() }.map { it.name to it.value }.toMutableList()
        var url = r.url
        r.pathParams.filter { it.enabled && it.value.isNotBlank() }.forEach { url = url.replace(":" + it.name, it.value) }
        val a = r.auth
        when (a.mode) {
            AuthMode.Bearer -> h += "Authorization" to "Bearer ${a.token}"
            AuthMode.Basic -> h += "Authorization" to "Basic " + java.util.Base64.getEncoder().encodeToString("${a.username}:${a.password}".toByteArray())
            AuthMode.ApiKey -> if (a.placement == "queryparams") url += (if (url.contains('?')) "&" else "?") + "${a.keyName}=${a.keyValue}" else h += a.keyName to a.keyValue
            else -> {}
        }
        val body = when (r.bodyMode) {
            BodyMode.None -> Body.None
            BodyMode.Json -> Body.Raw(r.bodyJson, "application/json")
            BodyMode.Text -> Body.Raw(r.bodyText, "text/plain")
            BodyMode.Xml -> Body.Raw(r.bodyXml, "application/xml")
            BodyMode.Graphql -> Body.Raw(Json.stringify(linkedMapOf("query" to r.bodyGraphql, "variables" to (Json.tryParse(r.bodyGraphqlVars) ?: emptyMap<String, Any?>()))), "application/json")
            BodyMode.Form -> Body.Form(r.formUrlEncoded.filter { it.enabled }.map { it.name to it.value })
            BodyMode.Multipart -> Body.Multipart(r.multipart.filter { it.enabled }.map { Part(it.name, it.value, it.isFile, it.contentType) })
            BodyMode.File -> Body.FileBody(r.bodyFile)
        }
        return PreparedRequest(r.method, url, h, body)
    }

    private fun flat(nodes: List<Node>, prefix: String = ""): List<Pair<String, RequestNode>> = nodes.flatMap {
        when (it) {
            is RequestNode -> listOf(prefix to it)
            is FolderNode -> flat(it.children, if (prefix.isEmpty()) it.name else "$prefix / ${it.name}")
        }
    }

    private fun curlScript(c: Collection): String = buildString {
        append("#!/usr/bin/env bash\n# ${c.name} — exported from OpenRest\n")
        val vars = (c.config.preVars.filter { it.enabled } + (c.environments.firstOrNull()?.vars ?: emptyList()))
        vars.forEach { append("${it.name.replace(Regex("[^A-Za-z0-9_]"), "_")}='${it.value.replace("'", "'\\''")}'\n") }
        append("\n")
        flat(c.children).forEach { (folder, n) ->
            append("# ${if (folder.isEmpty()) "" else "$folder / "}${n.name}\n")
            append(CodeGen.generate("cURL", raw(n.request))).append("\n\n")
        }
    }

    private fun httpFile(c: Collection): String = buildString {
        (c.config.preVars.filter { it.enabled } + (c.environments.firstOrNull()?.vars ?: emptyList())).forEach { append("@${it.name} = ${it.value}\n") }
        if (isNotEmpty()) append("\n")
        flat(c.children).forEach { (folder, n) ->
            val p = raw(n.request)
            append("### ${if (folder.isEmpty()) "" else "$folder / "}${n.name}\n")
            append("# @name ${n.name.replace(Regex("[^A-Za-z0-9_]"), "_")}\n")
            append("${p.method} ${p.url}\n")
            val hs = p.headers.toMutableList()
            when (val b = p.body) {
                is Body.Raw -> if (hs.none { it.first.equals("content-type", true) }) hs += "Content-Type" to b.contentType
                is Body.Form -> hs += "Content-Type" to "application/x-www-form-urlencoded"
                else -> {}
            }
            hs.forEach { append("${it.first}: ${it.second}\n") }
            when (val b = p.body) {
                is Body.Raw -> append("\n${b.text}\n")
                is Body.Form -> append("\n${HttpExecutor.formEncode(b.fields)}\n")
                is Body.FileBody -> append("\n< ${b.path}\n")
                is Body.Multipart -> {
                    val bd = "----OpenRestBoundary"
                    hs.removeAll { it.first.equals("content-type", true) }
                    append("Content-Type: multipart/form-data; boundary=$bd\n\n")
                    b.parts.forEach { pt ->
                        append("--$bd\n")
                        if (pt.isFile) append("Content-Disposition: form-data; name=\"${pt.name}\"; filename=\"${pt.value.substringAfterLast('/')}\"\n\n< ${pt.value}\n")
                        else append("Content-Disposition: form-data; name=\"${pt.name}\"\n\n${pt.value}\n")
                    }
                    append("--$bd--\n")
                }
                Body.None -> {}
            }
            append("\n")
        }
    }

    // ---------------- Insomnia ----------------

    private fun insomnia(c: Collection): Map<String, Any?> {
        val res = mutableListOf<Map<String, Any?>>()
        var n = 0
        val ws = "wrk_1"
        res += linkedMapOf("_id" to ws, "_type" to "workspace", "name" to c.name, "description" to c.config.docs, "scope" to "collection")
        fun walk(nodes: List<Node>, parent: String) {
            nodes.forEach { node ->
                when (node) {
                    is FolderNode -> { val id = "fld_${++n}"; res += linkedMapOf("_id" to id, "_type" to "request_group", "parentId" to parent, "name" to node.name); walk(node.children, id) }
                    is RequestNode -> {
                        val r = node.request
                        val body: Map<String, Any?> = when (r.bodyMode) {
                            BodyMode.Json -> mapOf("mimeType" to "application/json", "text" to r.bodyJson)
                            BodyMode.Text -> mapOf("mimeType" to "text/plain", "text" to r.bodyText)
                            BodyMode.Xml -> mapOf("mimeType" to "application/xml", "text" to r.bodyXml)
                            BodyMode.Graphql -> mapOf("mimeType" to "application/graphql", "text" to Json.stringify(linkedMapOf("query" to r.bodyGraphql, "variables" to (Json.tryParse(r.bodyGraphqlVars) ?: emptyMap<String, Any?>()))))
                            BodyMode.Form -> mapOf("mimeType" to "application/x-www-form-urlencoded", "params" to r.formUrlEncoded.map { mapOf("name" to it.name, "value" to it.value, "disabled" to !it.enabled) })
                            BodyMode.Multipart -> mapOf("mimeType" to "multipart/form-data", "params" to r.multipart.map { mapOf("name" to it.name, "value" to if (it.isFile) "" else it.value, "type" to if (it.isFile) "file" else "text", "fileName" to if (it.isFile) it.value else "", "disabled" to !it.enabled) })
                            else -> emptyMap()
                        }
                        val auth: Map<String, Any?> = when (r.auth.mode) {
                            AuthMode.Bearer -> mapOf("type" to "bearer", "token" to r.auth.token)
                            AuthMode.Basic -> mapOf("type" to "basic", "username" to r.auth.username, "password" to r.auth.password)
                            else -> emptyMap()
                        }
                        res += linkedMapOf(
                            "_id" to "req_${++n}", "_type" to "request", "parentId" to parent, "name" to r.name, "method" to r.method,
                            "url" to r.url.substringBefore('?'),
                            "parameters" to r.params.map { mapOf("name" to it.name, "value" to it.value, "disabled" to !it.enabled) },
                            "headers" to r.headers.map { mapOf("name" to it.name, "value" to it.value, "disabled" to !it.enabled) },
                            "body" to body, "authentication" to auth, "description" to r.docs,
                        )
                    }
                }
            }
        }
        walk(c.children, ws)
        val baseId = "env_base"
        res += linkedMapOf("_id" to baseId, "_type" to "environment", "parentId" to ws, "name" to "Base Environment", "data" to c.config.preVars.filter { it.enabled }.associate { it.name to it.value })
        c.environments.forEach { e -> res += linkedMapOf("_id" to "env_${++n}", "_type" to "environment", "parentId" to baseId, "name" to e.name, "data" to e.vars.filter { it.enabled }.associate { it.name to it.value }) }
        return linkedMapOf("_type" to "export", "__export_format" to 4L, "__export_source" to "openrest", "resources" to res)
    }

    // ---------------- OpenAPI 3 ----------------

    private fun schemaOf(v: Any?): Map<String, Any?> = when (v) {
        is Map<*, *> -> linkedMapOf("type" to "object", "properties" to v.entries.associate { it.key.toString() to schemaOf(it.value) })
        is List<*> -> linkedMapOf("type" to "array", "items" to schemaOf(v.firstOrNull()))
        is Long, is Int -> mapOf("type" to "integer")
        is Number -> mapOf("type" to "number")
        is Boolean -> mapOf("type" to "boolean")
        else -> mapOf("type" to "string")
    }

    private fun openApi(c: Collection): Map<String, Any?> {
        val vars = (c.config.preVars.filter { it.enabled } + (c.environments.firstOrNull()?.vars?.filter { it.enabled } ?: emptyList())).associate { it.name to it.value }
        val paths = linkedMapOf<String, LinkedHashMap<String, Any?>>()
        val schemes = linkedMapOf<String, Any?>()
        var server: String? = null
        val urlRe = Regex("^(https?://[^/]+|\\{\\{[^}]+}})(/.*)?$", RegexOption.DOT_MATCHES_ALL)
        flat(c.children).forEach { (folder, node) ->
            val r = node.request
            val rawUrl = r.url.substringBefore('?')
            val lead = Regex("^\\{\\{([^}]+)}}(/.*)?$", RegexOption.DOT_MATCHES_ALL).find(rawUrl)
            val base: String?
            var path: String
            if (lead != null && vars.containsKey(lead.groupValues[1])) {
                base = Variables.interpolate(vars[lead.groupValues[1]]!!) { vars[it] }.trimEnd('/')
                path = lead.groupValues[2].ifEmpty { "/" }
            } else {
                val url = Variables.interpolate(rawUrl) { vars[it] }
                val mm = urlRe.find(url)
                base = mm?.groupValues?.get(1)
                path = (if (mm != null) mm.groupValues[2].ifEmpty { "/" } else if (url.startsWith("/")) url else "/$url")
            }
            if (server == null && base != null) server = base
            path = path.replace(Regex("(?<=/):([A-Za-z_][\\w-]*)"), "{$1}").replace(Regex("\\{\\{([^}]+)}}"), "{$1}")
            val params = mutableListOf<Map<String, Any?>>()
            Regex("\\{([^}/]+)}").findAll(path).map { it.groupValues[1] }.distinct().forEach { pn ->
                params += Yml.m("name" to pn, "in" to "path", "required" to true, "schema" to mapOf("type" to "string"), "example" to r.pathParams.firstOrNull { it.name == pn }?.value)
            }
            r.params.filter { it.enabled && it.name.isNotBlank() }.forEach { params += Yml.m("name" to it.name, "in" to "query", "required" to false, "schema" to mapOf("type" to "string"), "example" to it.value) }
            r.headers.filter { it.enabled && it.name.lowercase() !in setOf("content-type", "accept", "authorization") }.forEach { params += Yml.m("name" to it.name, "in" to "header", "schema" to mapOf("type" to "string"), "example" to it.value) }
            val bodyContent: Map<String, Any?>? = when (r.bodyMode) {
                BodyMode.Json -> { val ex = Json.tryParse(r.bodyJson); mapOf("application/json" to Yml.m("schema" to (if (ex != null) schemaOf(ex) else mapOf("type" to "object")), "example" to (ex ?: r.bodyJson.ifEmpty { null }))) }
                BodyMode.Text -> mapOf("text/plain" to mapOf("schema" to mapOf("type" to "string"), "example" to r.bodyText))
                BodyMode.Xml -> mapOf("application/xml" to mapOf("schema" to mapOf("type" to "string"), "example" to r.bodyXml))
                BodyMode.Graphql -> mapOf("application/json" to mapOf("schema" to schemaOf(linkedMapOf("query" to "", "variables" to emptyMap<String, Any?>())), "example" to linkedMapOf("query" to r.bodyGraphql, "variables" to (Json.tryParse(r.bodyGraphqlVars) ?: emptyMap<String, Any?>()))))
                BodyMode.Form -> mapOf("application/x-www-form-urlencoded" to mapOf("schema" to linkedMapOf("type" to "object", "properties" to r.formUrlEncoded.filter { it.enabled }.associate { it.name to mapOf("type" to "string", "example" to it.value) })))
                BodyMode.Multipart -> mapOf("multipart/form-data" to mapOf("schema" to linkedMapOf("type" to "object", "properties" to r.multipart.filter { it.enabled }.associate { it.name to (if (it.isFile) mapOf("type" to "string", "format" to "binary") else mapOf("type" to "string", "example" to it.value)) })))
                BodyMode.File -> mapOf("application/octet-stream" to mapOf("schema" to mapOf("type" to "string", "format" to "binary")))
                BodyMode.None -> null
            }
            val auth = if (r.auth.mode == AuthMode.Inherit) c.config.auth else r.auth
            val sec: List<Map<String, Any?>>? = when (auth.mode) {
                AuthMode.Bearer -> { schemes["bearerAuth"] = mapOf("type" to "http", "scheme" to "bearer"); listOf(mapOf("bearerAuth" to emptyList<String>())) }
                AuthMode.Basic -> { schemes["basicAuth"] = mapOf("type" to "http", "scheme" to "basic"); listOf(mapOf("basicAuth" to emptyList<String>())) }
                AuthMode.Digest -> { schemes["digestAuth"] = mapOf("type" to "http", "scheme" to "digest"); listOf(mapOf("digestAuth" to emptyList<String>())) }
                AuthMode.ApiKey -> { schemes["apiKeyAuth"] = mapOf("type" to "apiKey", "in" to (if (auth.placement == "queryparams") "query" else "header"), "name" to auth.keyName); listOf(mapOf("apiKeyAuth" to emptyList<String>())) }
                else -> null
            }
            val op = Yml.m(
                "tags" to (if (folder.isEmpty()) null else listOf(folder)), "summary" to r.name, "description" to r.docs,
                "parameters" to params,
                "requestBody" to (bodyContent?.let { mapOf("content" to it) }),
                "responses" to mapOf("200" to mapOf("description" to "Successful response")),
                "security" to sec,
            )
            paths.getOrPut(path) { linkedMapOf() }[r.method.lowercase()] = op
        }
        return Yml.m(
            "openapi" to "3.0.3",
            "info" to Yml.m("title" to c.name, "description" to c.config.docs, "version" to "1.0.0"),
            "servers" to server?.let { listOf(mapOf("url" to it)) },
            "paths" to paths,
            "components" to Yml.m("securitySchemes" to schemes),
        )
    }
}
