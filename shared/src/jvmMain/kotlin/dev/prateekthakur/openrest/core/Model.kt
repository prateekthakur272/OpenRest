package dev.prateekthakur.openrest.core

import java.nio.file.Path

data class KV(
    val name: String = "",
    val value: String = "",
    val enabled: Boolean = true,
    val isFile: Boolean = false,
    val contentType: String = "",
)

enum class BodyMode(val label: String, val bru: String) {
    None("No Body", "none"),
    Json("JSON", "json"),
    Text("Text", "text"),
    Xml("XML", "xml"),
    Form("Form URL Encoded", "formUrlEncoded"),
    Multipart("Multipart Form", "multipartForm"),
    Graphql("GraphQL", "graphql"),
    File("File / Binary", "file");

    companion object {
        fun fromBru(s: String) = entries.firstOrNull { it.bru.equals(s, true) } ?: None
    }
}

enum class AuthMode(val label: String, val bru: String) {
    None("No Auth", "none"),
    Inherit("Inherit", "inherit"),
    Basic("Basic Auth", "basic"),
    Bearer("Bearer Token", "bearer"),
    ApiKey("API Key", "apikey"),
    Digest("Digest Auth", "digest"),
    OAuth2("OAuth 2.0", "oauth2");

    companion object {
        fun fromBru(s: String) = entries.firstOrNull { it.bru.equals(s, true) } ?: None
    }
}

data class Auth(
    val mode: AuthMode = AuthMode.None,
    val username: String = "",
    val password: String = "",
    val token: String = "",
    val keyName: String = "",
    val keyValue: String = "",
    val placement: String = "header", // header | queryparams
    val grantType: String = "client_credentials", // client_credentials | password
    val tokenUrl: String = "",
    val clientId: String = "",
    val clientSecret: String = "",
    val scope: String = "",
)

val HTTP_METHODS = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "TRACE")

data class ApiRequest(
    val name: String = "New Request",
    val seq: Int = 1,
    val method: String = "GET",
    val url: String = "",
    val params: List<KV> = emptyList(),
    val pathParams: List<KV> = emptyList(),
    val headers: List<KV> = emptyList(),
    val bodyMode: BodyMode = BodyMode.None,
    val bodyJson: String = "",
    val bodyText: String = "",
    val bodyXml: String = "",
    val bodyGraphql: String = "",
    val bodyGraphqlVars: String = "",
    val bodyFile: String = "",
    val formUrlEncoded: List<KV> = emptyList(),
    val multipart: List<KV> = emptyList(),
    val auth: Auth = Auth(AuthMode.Inherit),
    val preVars: List<KV> = emptyList(),
    val postVars: List<KV> = emptyList(),
    val assertions: List<KV> = emptyList(),
    val preScript: String = "",
    val postScript: String = "",
    val tests: String = "",
    val docs: String = "",
    val encodeUrl: Boolean = true,
    val timeoutMs: Int = 0,
)

/** Settings shared by a whole collection (collection.bru) or a folder (folder.bru). */
data class NodeConfig(
    val name: String = "",
    val seq: Int = 1,
    val headers: List<KV> = emptyList(),
    val auth: Auth = Auth(AuthMode.None),
    val preVars: List<KV> = emptyList(),
    val postVars: List<KV> = emptyList(),
    val preScript: String = "",
    val postScript: String = "",
    val tests: String = "",
    val docs: String = "",
)

data class Environment(
    val name: String,
    val vars: List<KV> = emptyList(),
    val secrets: Set<String> = emptySet(),
)

sealed interface Node {
    val path: Path
    val name: String
}

data class FolderNode(
    override val path: Path,
    override val name: String,
    val config: NodeConfig,
    val children: List<Node>,
) : Node

data class RequestNode(
    override val path: Path,
    override val name: String,
    val request: ApiRequest,
) : Node

data class Collection(
    val path: Path,
    val name: String,
    val config: NodeConfig,
    val children: List<Node>,
    val environments: List<Environment>,
    val dotEnv: Map<String, String> = emptyMap(),
    val workflows: List<Workflow> = emptyList(),
)

fun Collection.allRequests(): List<RequestNode> {
    fun walk(n: List<Node>): List<RequestNode> = n.flatMap { if (it is RequestNode) listOf(it) else walk((it as FolderNode).children) }
    return walk(children)
}

fun Collection.findRequest(path: Path): RequestNode? = allRequests().firstOrNull { it.path == path }

/** Folder configs from the collection root down to (not including) the node at [target]. */
fun Collection.ancestorFolders(target: Path): List<FolderNode> {
    val out = mutableListOf<FolderNode>()
    fun walk(list: List<Node>): Boolean {
        for (n in list) {
            if (n.path == target) return true
            if (n is FolderNode) {
                out.add(n)
                if (walk(n.children)) return true
                out.removeAt(out.size - 1)
            }
        }
        return false
    }
    walk(children)
    return out
}

// ---- Execution-related models ----

sealed interface Body {
    data object None : Body
    data class Raw(val text: String, val contentType: String) : Body
    data class Form(val fields: List<Pair<String, String>>) : Body
    data class Multipart(val parts: List<Part>) : Body
    data class FileBody(val path: String) : Body
}

data class Part(val name: String, val value: String, val isFile: Boolean, val contentType: String = "")

data class PreparedRequest(
    val method: String,
    val url: String,
    val headers: List<Pair<String, String>>,
    val body: Body,
    val timeoutMs: Int = 0,
)

data class HttpResult(
    val status: Int = 0,
    val statusText: String = "",
    val headers: List<Pair<String, String>> = emptyList(),
    val body: ByteArray = ByteArray(0),
    val timeMs: Long = 0,
    val finalUrl: String = "",
    val error: String? = null,
) {
    val contentType: String get() = headers.firstOrNull { it.first.equals("content-type", true) }?.second ?: ""
    val text: String get() = decode()
    val size: Int get() = body.size
    private fun decode(): String {
        val cs = Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE).find(contentType)?.groupValues?.get(1)
        return try { String(body, if (cs != null) charset(cs) else Charsets.UTF_8) } catch (_: Exception) { String(body, Charsets.UTF_8) }
    }
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
}

data class TestResult(val name: String, val passed: Boolean, val message: String = "", val kind: String = "test")

data class ExecResult(
    val request: PreparedRequest? = null,
    val response: HttpResult? = null,
    val tests: List<TestResult> = emptyList(),
    val logs: List<String> = emptyList(),
    val error: String? = null,
    val skipped: Boolean = false,
    val stop: Boolean = false,
    val nextRequest: String? = null,
)

/** How the app picks its colours: follow the operating system, or always light / dark. */
enum class ThemeMode(val id: String, val label: String) {
    System("system", "System"), Light("light", "Light"), Dark("dark", "Dark");

    fun isDark(systemIsDark: Boolean) = when (this) { System -> systemIsDark; Light -> false; Dark -> true }

    companion object { fun of(id: String?) = entries.firstOrNull { it.id == id } }
}

data class AppSettings(
    val theme: ThemeMode = ThemeMode.Dark,
    val timeoutMs: Int = 30000,
    val followRedirects: Boolean = true,
    val maxRedirects: Int = 10,
    val sslVerify: Boolean = true,
    val proxyEnabled: Boolean = false,
    val proxyHost: String = "",
    val proxyPort: Int = 8080,
    val userAgent: String = "OpenRest/1.0",
    val verticalLayout: Boolean = true,
    val fontSize: Int = 13,
    val autoSave: Boolean = false,
)
