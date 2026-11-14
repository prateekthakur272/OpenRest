package dev.prateekthakur.openrest.core.codegen

import dev.prateekthakur.openrest.core.http.*
import dev.prateekthakur.openrest.core.model.*

object CodeGen {
    val languages = listOf(
        "cURL", "JavaScript (fetch)", "JavaScript (axios)", "Node.js (http)", "Python (requests)", "Java (HttpClient)", "Kotlin (OkHttp)",
        "Go (net/http)", "PHP (cURL)", "C# (HttpClient)", "Ruby (Net::HTTP)", "Swift (URLSession)", "Rust (reqwest)", "Dart (http)", "PowerShell", "HTTPie", "wget",
    )

    /** Syntax-highlighter language id for a generator name. */
    fun langId(name: String) = when {
        name.startsWith("JavaScript") || name.startsWith("Node") -> "js"
        name.startsWith("Python") -> "py"; name.startsWith("Java ") || name.startsWith("Java(") -> "java"; name.startsWith("Kotlin") -> "kt"
        name.startsWith("Go") -> "go"; name.startsWith("PHP") -> "php"; name.startsWith("C#") -> "cs"; name.startsWith("Ruby") -> "rb"
        name.startsWith("Swift") -> "swift"; name.startsWith("Rust") -> "rs"; name.startsWith("Dart") -> "dart"; name.startsWith("PowerShell") -> "ps"
        else -> "sh"
    }

    private fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""
    private fun sh(s: String) = "'" + s.replace("'", "'\\''") + "'"

    private fun headersWithCt(r: PreparedRequest): List<Pair<String, String>> {
        val h = r.headers.toMutableList()
        fun has() = h.any { it.first.equals("content-type", true) }
        when (val b = r.body) {
            is Body.Raw -> if (!has() && b.contentType.isNotEmpty()) h += "Content-Type" to b.contentType
            is Body.Form -> if (!has()) h += "Content-Type" to "application/x-www-form-urlencoded"
            else -> {}
        }
        return h
    }

    private fun rawBody(r: PreparedRequest): String? = when (val b = r.body) {
        is Body.Raw -> b.text
        is Body.Form -> HttpExecutor.formEncode(b.fields)
        else -> null
    }

    fun generate(lang: String, r: PreparedRequest): String {
        val h = headersWithCt(r)
        val body = rawBody(r)
        val mp = (r.body as? Body.Multipart)?.parts
        val file = (r.body as? Body.FileBody)?.path
        val m = r.method
        return when (lang) {
            "cURL" -> buildString {
                append("curl")
                if (m != "GET") append(" -X $m")
                append(" ").append(sh(r.url))
                h.forEach { append(" \\\n  -H ").append(sh("${it.first}: ${it.second}")) }
                if (body != null) append(" \\\n  --data-raw ").append(sh(body))
                mp?.forEach { append(" \\\n  -F ").append(sh(if (it.isFile) "${it.name}=@${it.value}" else "${it.name}=${it.value}")) }
                if (file != null) append(" \\\n  --data-binary ").append(sh("@$file"))
            }
            "wget" -> buildString {
                append("wget --no-check-certificate --quiet \\\n  --method $m \\\n")
                h.forEach { append("  --header ").append(sh("${it.first}: ${it.second}")).append(" \\\n") }
                if (body != null) append("  --body-data ").append(sh(body)).append(" \\\n")
                append("  ").append(sh(r.url))
            }
            "HTTPie" -> buildString {
                append("http ")
                if (body != null && r.body is Body.Raw) append("--raw ").append(sh(body)).append(" ")
                append("$m ").append(sh(r.url))
                h.forEach { append(" \\\n  ").append(sh("${it.first}:${it.second}")) }
            }
            "JavaScript (fetch)" -> buildString {
                append("const options = {\n  method: ${q(m)},\n")
                if (h.isNotEmpty()) append("  headers: {\n").append(h.joinToString(",\n") { "    ${q(it.first)}: ${q(it.second)}" }).append("\n  },\n")
                if (body != null) append("  body: ${q(body)},\n")
                if (mp != null) {
                    append("  body: (() => {\n    const f = new FormData();\n")
                    mp.forEach { append(if (it.isFile) "    f.append(${q(it.name)}, /* file: ${it.value} */ new Blob([]));\n" else "    f.append(${q(it.name)}, ${q(it.value)});\n") }
                    append("    return f;\n  })(),\n")
                }
                append("};\n\nfetch(${q(r.url)}, options)\n  .then((res) => res.text())\n  .then((data) => console.log(data))\n  .catch((err) => console.error(err));")
            }
            "JavaScript (axios)" -> buildString {
                append("const axios = require('axios');\n\nconst config = {\n  method: ${q(m.lowercase())},\n  url: ${q(r.url)},\n")
                if (h.isNotEmpty()) append("  headers: {\n").append(h.joinToString(",\n") { "    ${q(it.first)}: ${q(it.second)}" }).append("\n  },\n")
                if (body != null) append("  data: ${q(body)},\n")
                append("};\n\naxios(config)\n  .then((response) => console.log(response.data))\n  .catch((error) => console.error(error));")
            }
            "Node.js (http)" -> buildString {
                val u = runCatching { java.net.URI(r.url) }.getOrNull()
                val https = u?.scheme == "https"
                append("const ${if (https) "https" else "http"} = require('${if (https) "https" else "http"}');\n\n")
                append("const options = {\n  method: ${q(m)},\n  hostname: ${q(u?.host ?: "")},\n")
                if ((u?.port ?: -1) > 0) append("  port: ${u!!.port},\n")
                append("  path: ${q((u?.rawPath?.ifEmpty { "/" } ?: "/") + (u?.rawQuery?.let { "?$it" } ?: ""))},\n")
                append("  headers: {\n").append(h.joinToString(",\n") { "    ${q(it.first)}: ${q(it.second)}" }).append("\n  },\n};\n\n")
                append("const req = ${if (https) "https" else "http"}.request(options, (res) => {\n  const chunks = [];\n  res.on('data', (c) => chunks.push(c));\n  res.on('end', () => console.log(Buffer.concat(chunks).toString()));\n});\n\n")
                if (body != null) append("req.write(${q(body)});\n")
                append("req.end();")
            }
            "Python (requests)" -> buildString {
                append("import requests\n\nurl = ${q(r.url)}\n")
                if (h.isNotEmpty()) append("headers = {\n").append(h.joinToString(",\n") { "    ${q(it.first)}: ${q(it.second)}" }).append(",\n}\n") else append("headers = {}\n")
                if (body != null) append("payload = ${q(body)}\n")
                if (mp != null) {
                    append("files = [\n").append(mp.filter { it.isFile }.joinToString(",\n") { "    (${q(it.name)}, open(${q(it.value)}, 'rb'))" }).append("\n]\n")
                    append("data = {\n").append(mp.filter { !it.isFile }.joinToString(",\n") { "    ${q(it.name)}: ${q(it.value)}" }).append("\n}\n")
                }
                append("\nresponse = requests.request(${q(m)}, url, headers=headers")
                if (body != null) append(", data=payload")
                if (mp != null) append(", data=data, files=files")
                if (file != null) append(", data=open(${q(file)}, 'rb')")
                append(")\nprint(response.text)")
            }
            "Java (HttpClient)" -> buildString {
                append("import java.net.URI;\nimport java.net.http.*;\n\nHttpClient client = HttpClient.newHttpClient();\nHttpRequest request = HttpRequest.newBuilder()\n    .uri(URI.create(${q(r.url)}))\n")
                h.forEach { append("    .header(${q(it.first)}, ${q(it.second)})\n") }
                append("    .method(${q(m)}, ${if (body != null) "HttpRequest.BodyPublishers.ofString(${q(body)})" else "HttpRequest.BodyPublishers.noBody()"})\n    .build();\n")
                append("HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());\nSystem.out.println(response.body());")
            }
            "Kotlin (OkHttp)" -> buildString {
                append("import okhttp3.*\nimport okhttp3.MediaType.Companion.toMediaType\nimport okhttp3.RequestBody.Companion.toRequestBody\n\nval client = OkHttpClient()\n")
                val ct = h.firstOrNull { it.first.equals("content-type", true) }?.second ?: "text/plain"
                append("val request = Request.Builder()\n    .url(${q(r.url)})\n")
                h.forEach { append("    .addHeader(${q(it.first)}, ${q(it.second)})\n") }
                append("    .method(${q(m)}, ${if (body != null) "${q(body)}.toRequestBody(${q(ct)}.toMediaType())" else if (m in listOf("POST", "PUT", "PATCH")) "\"\".toRequestBody(null)" else "null"})\n    .build()\n")
                append("client.newCall(request).execute().use { println(it.body?.string()) }")
            }
            "Go (net/http)" -> buildString {
                append("package main\n\nimport (\n\t\"fmt\"\n\t\"io\"\n\t\"net/http\"\n${if (body != null) "\t\"strings\"\n" else ""})\n\nfunc main() {\n")
                append("\tclient := &http.Client{}\n")
                if (body != null) append("\tpayload := strings.NewReader(${q(body)})\n\treq, err := http.NewRequest(${q(m)}, ${q(r.url)}, payload)\n")
                else append("\treq, err := http.NewRequest(${q(m)}, ${q(r.url)}, nil)\n")
                append("\tif err != nil {\n\t\tpanic(err)\n\t}\n")
                h.forEach { append("\treq.Header.Add(${q(it.first)}, ${q(it.second)})\n") }
                append("\tres, err := client.Do(req)\n\tif err != nil {\n\t\tpanic(err)\n\t}\n\tdefer res.Body.Close()\n\tb, _ := io.ReadAll(res.Body)\n\tfmt.Println(string(b))\n}")
            }
            "PHP (cURL)" -> buildString {
                append("<?php\n\n\$curl = curl_init();\ncurl_setopt_array(\$curl, [\n  CURLOPT_URL => ${q(r.url).replace("$", "\\$")},\n  CURLOPT_RETURNTRANSFER => true,\n  CURLOPT_CUSTOMREQUEST => ${q(m)},\n")
                if (body != null) append("  CURLOPT_POSTFIELDS => ${q(body).replace("$", "\\$")},\n")
                if (h.isNotEmpty()) append("  CURLOPT_HTTPHEADER => [\n").append(h.joinToString(",\n") { "    ${q("${it.first}: ${it.second}").replace("$", "\\$")}" }).append("\n  ],\n")
                append("]);\n\$response = curl_exec(\$curl);\ncurl_close(\$curl);\necho \$response;")
            }
            "C# (HttpClient)" -> buildString {
                append("using System.Net.Http;\n\nvar client = new HttpClient();\nvar request = new HttpRequestMessage(new HttpMethod(${q(m)}), ${q(r.url)});\n")
                h.filter { !it.first.equals("content-type", true) }.forEach { append("request.Headers.TryAddWithoutValidation(${q(it.first)}, ${q(it.second)});\n") }
                if (body != null) {
                    val ct = h.firstOrNull { it.first.equals("content-type", true) }?.second ?: "text/plain"
                    append("request.Content = new StringContent(${q(body)}, System.Text.Encoding.UTF8, ${q(ct)});\n")
                }
                append("var response = await client.SendAsync(request);\nresponse.EnsureSuccessStatusCode();\nConsole.WriteLine(await response.Content.ReadAsStringAsync());")
            }
            "Ruby (Net::HTTP)" -> buildString {
                append("require 'uri'\nrequire 'net/http'\n\nurl = URI(${q(r.url)})\nhttp = Net::HTTP.new(url.host, url.port)\nhttp.use_ssl = url.scheme == 'https'\n\n")
                append("request = Net::HTTP::${m.lowercase().replaceFirstChar { it.uppercase() }}.new(url)\n")
                h.forEach { append("request[${q(it.first)}] = ${q(it.second)}\n") }
                if (body != null) append("request.body = ${q(body)}\n")
                append("\nresponse = http.request(request)\nputs response.read_body")
            }
            "Swift (URLSession)" -> buildString {
                append("import Foundation\n\nvar request = URLRequest(url: URL(string: ${q(r.url)})!)\nrequest.httpMethod = ${q(m)}\n")
                h.forEach { append("request.addValue(${q(it.second)}, forHTTPHeaderField: ${q(it.first)})\n") }
                if (body != null) append("request.httpBody = ${q(body)}.data(using: .utf8)\n")
                append("\nlet task = URLSession.shared.dataTask(with: request) { data, response, error in\n    if let data = data { print(String(data: data, encoding: .utf8)!) }\n}\ntask.resume()")
            }
            "Rust (reqwest)" -> buildString {
                append("let client = reqwest::Client::new();\nlet res = client\n    .request(reqwest::Method::from_bytes(b${q(m)}).unwrap(), ${q(r.url)})\n")
                h.forEach { append("    .header(${q(it.first)}, ${q(it.second)})\n") }
                if (body != null) append("    .body(${q(body)})\n")
                append("    .send()\n    .await?;\nprintln!(\"{}\", res.text().await?);")
            }
            "Dart (http)" -> buildString {
                append("import 'package:http/http.dart' as http;\n\nvoid main() async {\n  final request = http.Request(${q(m)}, Uri.parse(${q(r.url)}));\n")
                if (h.isNotEmpty()) append("  request.headers.addAll({\n").append(h.joinToString(",\n") { "    ${q(it.first)}: ${q(it.second)}" }).append("\n  });\n")
                if (body != null) append("  request.body = ${q(body)};\n")
                append("  final response = await request.send();\n  print(await response.stream.bytesToString());\n}")
            }
            "PowerShell" -> buildString {
                append("\$headers = @{\n").append(h.joinToString("\n") { "  ${q(it.first)} = ${q(it.second)}" }).append("\n}\n")
                if (body != null) append("\$body = ${q(body)}\n")
                append("\$response = Invoke-RestMethod -Uri ${q(r.url)} -Method $m -Headers \$headers")
                if (body != null) append(" -Body \$body")
            }
            else -> "// Unsupported language: $lang"
        }
    }
}
