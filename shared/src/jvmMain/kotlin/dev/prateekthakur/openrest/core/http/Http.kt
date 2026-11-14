package dev.prateekthakur.openrest.core.http

import dev.prateekthakur.openrest.core.model.*

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Paths
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object Variables {
    private val re = Regex("\\{\\{\\s*([^{}]+?)\\s*}}")
    private val rnd = java.util.Random()

    fun dynamic(name: String): String? = when (name) {
        "${'$'}guid", "${'$'}randomUUID" -> UUID.randomUUID().toString()
        "${'$'}timestamp" -> (System.currentTimeMillis() / 1000).toString()
        "${'$'}isoTimestamp" -> java.time.Instant.now().toString()
        "${'$'}randomInt" -> rnd.nextInt(1000).toString()
        "${'$'}randomBoolean" -> rnd.nextBoolean().toString()
        "${'$'}randomEmail" -> "user${rnd.nextInt(100000)}@example.com"
        "${'$'}randomFirstName" -> listOf("Ava", "Liam", "Noah", "Mia", "Zoe", "Ethan").random()
        "${'$'}randomLastName" -> listOf("Smith", "Jones", "Brown", "Lee", "Khan", "Patel").random()
        else -> null
    }

    fun interpolate(text: String, lookup: (String) -> String?): String {
        var cur = text
        repeat(4) {
            if (!cur.contains("{{")) return cur
            val next = re.replace(cur) { m ->
                val n = m.groupValues[1]
                lookup(n) ?: dynamic(n) ?: m.value
            }
            if (next == cur) return cur
            cur = next
        }
        return cur
    }

    fun names(text: String): List<String> = re.findAll(text).map { it.groupValues[1] }.toList()
}

object HttpExecutor {
    init {
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host,connection,content-length,expect,upgrade")
    }

    val cookies = CookieManager(null, CookiePolicy.ACCEPT_ALL)

    private var cached: Pair<String, HttpClient>? = null

    private fun client(s: AppSettings): HttpClient {
        val key = "${s.followRedirects}|${s.sslVerify}|${s.proxyEnabled}|${s.proxyHost}|${s.proxyPort}"
        cached?.let { if (it.first == key) return it.second }
        val b = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .cookieHandler(cookies)
            .followRedirects(if (s.followRedirects) HttpClient.Redirect.NORMAL else HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(30))
        if (!s.sslVerify) {
            System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true")
            val tm = object : X509TrustManager {
                override fun checkClientTrusted(c: Array<X509Certificate>?, a: String?) {}
                override fun checkServerTrusted(c: Array<X509Certificate>?, a: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf<TrustManager>(tm), SecureRandom())
            b.sslContext(ctx)
        }
        if (s.proxyEnabled && s.proxyHost.isNotBlank()) b.proxy(ProxySelector.of(InetSocketAddress(s.proxyHost, s.proxyPort)))
        return b.build().also { cached = key to it }
    }

    fun urlEncode(s: String): String = URLEncoder.encode(s, Charsets.UTF_8).replace("+", "%20")

    fun formEncode(fields: List<Pair<String, String>>): String =
        fields.joinToString("&") { URLEncoder.encode(it.first, Charsets.UTF_8) + "=" + URLEncoder.encode(it.second, Charsets.UTF_8) }

    fun buildBody(body: Body, headers: MutableList<Pair<String, String>>): HttpRequest.BodyPublisher {
        fun setCt(ct: String) { if (headers.none { it.first.equals("content-type", true) }) headers += "Content-Type" to ct }
        return when (body) {
            Body.None -> HttpRequest.BodyPublishers.noBody()
            is Body.Raw -> { if (body.contentType.isNotEmpty()) setCt(body.contentType); HttpRequest.BodyPublishers.ofString(body.text) }
            is Body.Form -> { setCt("application/x-www-form-urlencoded"); HttpRequest.BodyPublishers.ofString(formEncode(body.fields)) }
            is Body.FileBody -> {
                val p = Paths.get(body.path)
                setCt("application/octet-stream")
                HttpRequest.BodyPublishers.ofByteArray(Files.readAllBytes(p))
            }
            is Body.Multipart -> {
                val boundary = "----OpenRestBoundary" + UUID.randomUUID().toString().replace("-", "")
                headers.removeAll { it.first.equals("content-type", true) }
                headers += "Content-Type" to "multipart/form-data; boundary=$boundary"
                val out = ByteArrayOutputStream()
                fun w(s: String) = out.write(s.toByteArray())
                for (p in body.parts) {
                    w("--$boundary\r\n")
                    if (p.isFile) {
                        val f = Paths.get(p.value)
                        w("Content-Disposition: form-data; name=\"${p.name}\"; filename=\"${f.fileName}\"\r\n")
                        w("Content-Type: ${p.contentType.ifEmpty { Files.probeContentType(f) ?: "application/octet-stream" }}\r\n\r\n")
                        out.write(Files.readAllBytes(f))
                    } else {
                        w("Content-Disposition: form-data; name=\"${p.name}\"\r\n")
                        if (p.contentType.isNotEmpty()) w("Content-Type: ${p.contentType}\r\n")
                        w("\r\n")
                        w(p.value)
                    }
                    w("\r\n")
                }
                w("--$boundary--\r\n")
                HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())
            }
        }
    }

    fun execute(req: PreparedRequest, settings: AppSettings): HttpResult {
        val start = System.nanoTime()
        fun elapsed() = (System.nanoTime() - start) / 1_000_000
        try {
            val headers = req.headers.toMutableList()
            val publisher = buildBody(req.body, headers)
            val uri = try { URI.create(req.url.trim()) } catch (e: Exception) { return HttpResult(error = "Invalid URL: ${e.message}", timeMs = elapsed()) }
            if (uri.scheme == null || uri.host == null) return HttpResult(error = "Invalid URL: scheme and host are required (e.g. https://example.com)", timeMs = elapsed())
            val b = HttpRequest.newBuilder(uri)
            val hasMethodBody = req.body !is Body.None
            b.method(req.method.uppercase(), if (hasMethodBody) publisher else HttpRequest.BodyPublishers.noBody())
            val timeout = if (req.timeoutMs > 0) req.timeoutMs else settings.timeoutMs
            if (timeout > 0) b.timeout(Duration.ofMillis(timeout.toLong()))
            val names = headers.map { it.first.lowercase() }.toSet()
            for ((k, v) in headers) {
                if (k.isBlank() || k.equals("content-length", true)) continue
                runCatching { b.header(k, v) }
            }
            if ("user-agent" !in names) b.header("User-Agent", settings.userAgent)
            if ("accept" !in names) b.header("Accept", "*/*")
            if ("accept-encoding" !in names) b.header("Accept-Encoding", "gzip, deflate")
            val resp = client(settings).send(b.build(), HttpResponse.BodyHandlers.ofByteArray())
            var data = resp.body() ?: ByteArray(0)
            val enc = resp.headers().firstValue("content-encoding").orElse("").lowercase()
            runCatching {
                data = when {
                    enc.contains("gzip") -> GZIPInputStream(ByteArrayInputStream(data)).readBytes()
                    enc.contains("deflate") -> InflaterInputStream(ByteArrayInputStream(data)).readBytes()
                    else -> data
                }
            }
            val hs = resp.headers().map().flatMap { (k, vs) -> vs.map { k to it } }
            return HttpResult(resp.statusCode(), statusText(resp.statusCode()), hs, data, elapsed(), resp.uri().toString())
        } catch (e: java.net.http.HttpTimeoutException) {
            return HttpResult(error = "Request timed out", timeMs = elapsed())
        } catch (e: java.net.ConnectException) {
            return HttpResult(error = "Could not connect: ${e.message ?: e.javaClass.simpleName}", timeMs = elapsed())
        } catch (e: Exception) {
            val cause = generateSequence<Throwable>(e) { it.cause }.last()
            return HttpResult(error = "${cause.javaClass.simpleName}: ${cause.message ?: e.message}", timeMs = elapsed())
        }
    }

    fun statusText(code: Int) = when (code) {
        100 -> "Continue"; 101 -> "Switching Protocols"
        200 -> "OK"; 201 -> "Created"; 202 -> "Accepted"; 203 -> "Non-Authoritative Information"; 204 -> "No Content"; 205 -> "Reset Content"; 206 -> "Partial Content"
        300 -> "Multiple Choices"; 301 -> "Moved Permanently"; 302 -> "Found"; 303 -> "See Other"; 304 -> "Not Modified"; 307 -> "Temporary Redirect"; 308 -> "Permanent Redirect"
        400 -> "Bad Request"; 401 -> "Unauthorized"; 402 -> "Payment Required"; 403 -> "Forbidden"; 404 -> "Not Found"; 405 -> "Method Not Allowed"
        406 -> "Not Acceptable"; 408 -> "Request Timeout"; 409 -> "Conflict"; 410 -> "Gone"; 413 -> "Payload Too Large"; 415 -> "Unsupported Media Type"
        418 -> "I'm a teapot"; 422 -> "Unprocessable Entity"; 429 -> "Too Many Requests"
        500 -> "Internal Server Error"; 501 -> "Not Implemented"; 502 -> "Bad Gateway"; 503 -> "Service Unavailable"; 504 -> "Gateway Timeout"
        else -> ""
    }
}
