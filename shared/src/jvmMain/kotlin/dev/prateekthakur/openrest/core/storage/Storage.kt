package dev.prateekthakur.openrest.core.storage

import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.model.Collection
import dev.prateekthakur.openrest.core.run.*
import dev.prateekthakur.openrest.utils.*

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.io.path.*

/** App-level persistence (~/.openrest): settings, opened collections, secrets, selected environments. */
object AppStore {
    val dir: Path = (System.getProperty("openrest.home")?.let { Paths.get(it) } ?: Paths.get(System.getProperty("user.home"), ".openrest")).also { it.createDirectories() }
    private val settingsFile = dir.resolve("settings.json")
    private val stateFile = dir.resolve("state.json")
    private val secretsFile = dir.resolve("secrets.json")

    /** True when nothing has been saved yet: a fresh download that should show the walkthrough. */
    fun isFirstRun(): Boolean = !settingsFile.exists() && !stateFile.exists()

    fun loadSettings(): AppSettings = try {
        val m = Json.obj(Json.parse(settingsFile.readText())) ?: return AppSettings()
        val d = AppSettings()
        AppSettings(
            // files from before "System" existed stored a plain "dark": true / false
            theme = ThemeMode.of(m["theme"] as? String) ?: (m["dark"] as? Boolean)?.let { if (it) ThemeMode.Dark else ThemeMode.Light } ?: d.theme,
            timeoutMs = (m["timeoutMs"] as? Number)?.toInt() ?: d.timeoutMs,
            followRedirects = m["followRedirects"] as? Boolean ?: d.followRedirects,
            maxRedirects = (m["maxRedirects"] as? Number)?.toInt() ?: d.maxRedirects,
            sslVerify = m["sslVerify"] as? Boolean ?: d.sslVerify,
            proxyEnabled = m["proxyEnabled"] as? Boolean ?: d.proxyEnabled,
            proxyHost = m["proxyHost"] as? String ?: d.proxyHost,
            proxyPort = (m["proxyPort"] as? Number)?.toInt() ?: d.proxyPort,
            userAgent = m["userAgent"] as? String ?: d.userAgent,
            verticalLayout = m["verticalLayout"] as? Boolean ?: d.verticalLayout,
            fontSize = (m["fontSize"] as? Number)?.toInt() ?: d.fontSize,
            autoSave = m["autoSave"] as? Boolean ?: d.autoSave,
        )
    } catch (_: Exception) { AppSettings() }

    fun saveSettings(s: AppSettings) {
        val m = linkedMapOf<String, Any?>(
            "theme" to s.theme.id, "timeoutMs" to s.timeoutMs, "followRedirects" to s.followRedirects,
            "maxRedirects" to s.maxRedirects, "sslVerify" to s.sslVerify, "proxyEnabled" to s.proxyEnabled,
            "proxyHost" to s.proxyHost, "proxyPort" to s.proxyPort, "userAgent" to s.userAgent,
            "verticalLayout" to s.verticalLayout, "fontSize" to s.fontSize, "autoSave" to s.autoSave,
        )
        runCatching { settingsFile.writeText(Json.stringify(m, true)) }
    }

    data class SavedState(
        val collections: List<String>, val envs: Map<String, String>,
        val tabs: List<String> = emptyList(), val activeTab: String? = null,
        val sidebarWidth: Float = 0.22f, val splitFraction: Float = 0.45f, val sidebarVisible: Boolean = true,
        val globalEnv: String? = null,
    )

    fun loadState(): SavedState = try {
        val m = Json.obj(Json.parse(stateFile.readText()))!!
        SavedState(
            Json.arr(m["collections"])?.mapNotNull { it as? String } ?: emptyList(),
            Json.obj(m["envs"])?.mapValues { it.value.toString() } ?: emptyMap(),
            Json.arr(m["tabs"])?.mapNotNull { it as? String } ?: emptyList(),
            m["activeTab"] as? String,
            (m["sidebarWidth"] as? Number)?.toFloat() ?: 0.22f,
            (m["splitFraction"] as? Number)?.toFloat() ?: 0.45f,
            m["sidebarVisible"] as? Boolean ?: true,
            m["globalEnv"] as? String,
        )
    } catch (_: Exception) { SavedState(emptyList(), emptyMap()) }

    fun saveState(s: SavedState) {
        runCatching { stateFile.writeText(Json.stringify(linkedMapOf("collections" to s.collections, "envs" to s.envs, "tabs" to s.tabs, "activeTab" to s.activeTab, "sidebarWidth" to s.sidebarWidth.toDouble(), "splitFraction" to s.splitFraction.toDouble(), "sidebarVisible" to s.sidebarVisible, "globalEnv" to s.globalEnv), true)) }
    }

    private fun secretsMap(): MutableMap<String, Any?> =
        try { (Json.obj(Json.parse(secretsFile.readText())) ?: emptyMap()).toMutableMap() } catch (_: Exception) { mutableMapOf() }

    fun secretKey(collection: Path, env: String, name: String) = "$collection|$env|$name"

    fun getSecret(collection: Path, env: String, name: String): String =
        secretsMap()[secretKey(collection, env, name)] as? String ?: ""

    fun removeSecrets(collection: Path, env: String) {
        val m = secretsMap()
        val prefix = "$collection|$env|"
        if (m.keys.removeIf { it.startsWith(prefix) }) runCatching { secretsFile.writeText(Json.stringify(m, true)) }
    }

    /** Global environments (available to every collection) live next to settings in ~/.openrest/environments. */
    val globalEnvDir: Path get() = dir.resolve("environments")
    fun loadGlobalEnvs(): List<Environment> = EnvStore.load(globalEnvDir, EnvStore.GLOBAL_SCOPE)
    fun saveGlobalEnv(env: Environment, old: String? = null) = EnvStore.save(globalEnvDir, EnvStore.GLOBAL_SCOPE, env, old)
    fun deleteGlobalEnv(name: String) = EnvStore.delete(globalEnvDir, EnvStore.GLOBAL_SCOPE, name)

    fun setSecret(collection: Path, env: String, name: String, value: String) {
        val m = secretsMap()
        m[secretKey(collection, env, name)] = value
        runCatching { secretsFile.writeText(Json.stringify(m, true)) }
    }
}

/** Filesystem operations for OpenRest YAML collections. */
object Collections {
    private val IGNORED = setOf("node_modules", ".git", "environments", "workflows")
    private val RESERVED = setOf(OrYaml.ROOT_FILE, OrYaml.FOLDER_FILE)
    private const val EXT = ".yaml"

    fun sanitize(name: String): String =
        name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "-").trim('.', ' ').ifEmpty { "untitled" }

    private fun readMap(f: Path): Map<*, *>? = runCatching { Yml.parse(f.readText()) as? Map<*, *> }.getOrNull()

    fun create(parent: Path, name: String): Path {
        val dir = parent.resolve(sanitize(name))
        require(!dir.exists()) { "A folder named '${dir.name}' already exists" }
        dir.createDirectories()
        writeRoot(dir, NodeConfig(name = name), listOf("node_modules", ".git"))
        return dir
    }

    private fun writeRoot(dir: Path, cfg: NodeConfig, ignore: List<String>) {
        val m = linkedMapOf<String, Any?>("openrest" to OrYaml.VERSION.toLong())
        m.putAll(OrYaml.configToMap(cfg, false))
        if (ignore.isNotEmpty()) m["ignore"] = ignore
        dir.resolve(OrYaml.ROOT_FILE).writeText(Yml.dump(m))
    }

    fun isCollection(dir: Path) = dir.resolve(OrYaml.ROOT_FILE).exists()

    fun load(dir: Path): Collection {
        val root = readMap(dir.resolve(OrYaml.ROOT_FILE)) ?: emptyMap<String, Any?>()
        val cfg = OrYaml.mapToConfig(root, dir.name)
        val envs = EnvStore.load(dir.resolve("environments"), dir)
        val dotEnv = dir.resolve(".env").takeIf { it.exists() }?.readLines()?.mapNotNull {
            val l = it.trim()
            if (l.isEmpty() || l.startsWith("#") || !l.contains('=')) null
            else l.substringBefore('=').trim() to l.substringAfter('=').trim().trim('"', '\'')
        }?.toMap() ?: emptyMap()
        return Collection(dir, cfg.name, cfg, loadChildren(dir), envs, dotEnv, WorkflowStore.load(dir))
    }

    private fun loadChildren(dir: Path): List<Node> {
        val entries = runCatching { dir.listDirectoryEntries() }.getOrDefault(emptyList())
        val nodes = mutableListOf<Pair<Int, Node>>()
        for (e in entries) {
            if (e.name.startsWith(".")) continue
            if (e.isDirectory()) {
                if (e.name in IGNORED) continue
                val fb = e.resolve(OrYaml.FOLDER_FILE)
                val cfg = readMap(fb)?.let { OrYaml.mapToConfig(it, e.name) } ?: NodeConfig(name = e.name, seq = Int.MAX_VALUE)
                nodes += cfg.seq to FolderNode(e, cfg.name.ifEmpty { e.name }, cfg, loadChildren(e))
            } else if ((e.extension == "yaml" || e.extension == "yml") && e.name !in RESERVED) {
                val r = readRequest(e) ?: continue
                nodes += r.seq to RequestNode(e, r.name, r)
            }
        }
        // Requests first (by seq), then folders (by seq, then name)
        return nodes.sortedWith(compareBy<Pair<Int, Node>>({ if (it.second is FolderNode) 1 else 0 }, { it.first }, { it.second.name.lowercase() })).map { it.second }
    }

    fun requestFiles(dir: Path): List<Path> = runCatching {
        dir.listDirectoryEntries().filter { it.isRegularFile() && (it.extension == "yaml" || it.extension == "yml") && it.name !in RESERVED }
    }.getOrDefault(emptyList())

    fun readRequest(f: Path): ApiRequest? = runCatching { OrYaml.parseRequest(f.readText(), f.nameWithoutExtension) }.getOrNull()

    fun writeRequest(f: Path, r: ApiRequest) {
        f.parent.createDirectories()
        f.writeText(OrYaml.requestText(r))
    }

    fun uniqueFile(dir: Path, base: String, ext: String = EXT): Path {
        var p = dir.resolve(sanitize(base) + ext)
        var i = 1
        while (p.exists() || p.name in RESERVED) p = dir.resolve("${sanitize(base)} (${i++})$ext")
        return p
    }

    fun nextSeq(dir: Path): Int {
        val seqs = runCatching { dir.listDirectoryEntries("*.y*ml") }.getOrDefault(emptyList()).filter { it.name !in RESERVED }
            .mapNotNull { readRequest(it)?.seq }
        return (seqs.maxOrNull() ?: 0) + 1
    }

    fun saveRequest(file: Path, r: ApiRequest) = writeRequest(file, r)

    fun createRequest(dir: Path, r: ApiRequest): Path {
        val f = uniqueFile(dir, r.name)
        writeRequest(f, r.copy(seq = nextSeq(dir)))
        return f
    }

    fun createFolder(parent: Path, name: String): Path {
        val d = parent.resolve(sanitize(name))
        require(!d.exists()) { "Already exists: ${d.name}" }
        d.createDirectories()
        saveFolderConfig(d, NodeConfig(name = name, seq = 1))
        return d
    }

    fun renameRequest(file: Path, newName: String): Path {
        val r = (readRequest(file) ?: error("Cannot read ${file.name}")).copy(name = newName)
        val target = if (sanitize(newName) == file.nameWithoutExtension) file else uniqueFile(file.parent, newName)
        writeRequest(file, r)
        if (target != file) Files.move(file, target)
        return target
    }

    fun renameFolder(dir: Path, newName: String): Path {
        val fb = dir.resolve(OrYaml.FOLDER_FILE)
        val cfg = (readMap(fb)?.let { OrYaml.mapToConfig(it, dir.name) } ?: NodeConfig(name = dir.name)).copy(name = newName)
        saveFolderConfig(dir, cfg)
        val target = dir.parent.resolve(sanitize(newName))
        if (target != dir && !target.exists()) { Files.move(dir, target); return target }
        return dir
    }

    fun saveFolderConfig(dir: Path, cfg: NodeConfig) = dir.resolve(OrYaml.FOLDER_FILE).writeText(Yml.dump(OrYaml.configToMap(cfg, true)))

    fun saveCollectionConfig(dir: Path, cfg: NodeConfig) {
        val old = readMap(dir.resolve(OrYaml.ROOT_FILE))
        writeRoot(dir, cfg, (old?.get("ignore") as? List<*>)?.map { it.toString() } ?: listOf("node_modules", ".git"))
    }

    fun renameCollection(dir: Path, name: String) {
        val old = readMap(dir.resolve(OrYaml.ROOT_FILE))?.let { OrYaml.mapToConfig(it, dir.name) } ?: NodeConfig()
        saveCollectionConfig(dir, old.copy(name = name))
    }

    fun delete(p: Path) {
        if (p.isDirectory()) p.toFile().deleteRecursively() else p.deleteIfExists()
    }

    fun clone(file: Path, newName: String): Path {
        val r = (readRequest(file) ?: error("Cannot read ${file.name}")).copy(name = newName, seq = nextSeq(file.parent))
        val t = uniqueFile(file.parent, newName)
        writeRequest(t, r)
        return t
    }

    fun cloneFolder(dir: Path, newName: String): Path {
        val t = dir.parent.resolve(sanitize(newName)).let { if (it.exists()) dir.parent.resolve(sanitize(newName) + " copy") else it }
        dir.toFile().copyRecursively(t.toFile())
        renameFolder(t, newName)
        return t
    }

    fun move(src: Path, destDir: Path): Path {
        val t = destDir.resolve(src.name)
        require(!t.exists()) { "Target already exists" }
        require(!destDir.startsWith(src)) { "Cannot move into itself" }
        Files.move(src, t, StandardCopyOption.ATOMIC_MOVE)
        return t
    }

    /** Swap seq numbers of a request with its neighbour in the same folder. */
    fun reorder(siblings: List<Node>, index: Int, delta: Int) {
        val target = index + delta
        if (target !in siblings.indices) return
        val reqs = siblings.filterIsInstance<RequestNode>().toMutableList()
        val a = siblings[index] as? RequestNode ?: return
        val b = siblings[target] as? RequestNode ?: return
        val ia = reqs.indexOf(a); val ib = reqs.indexOf(b)
        reqs[ia] = b; reqs[ib] = a
        reqs.forEachIndexed { i, n -> saveRequest(n.path, n.request.copy(seq = i + 1)) }
    }

    // ---- environments ----

    fun saveEnvironment(col: Path, env: Environment, oldName: String? = null) =
        EnvStore.save(col.resolve("environments"), col, env, oldName)

    fun deleteEnvironment(col: Path, name: String) = EnvStore.delete(col.resolve("environments"), col, name)
}

/**
 * Reads/writes environment YAML files in a directory. Used for both scopes:
 * collection environments (`<collection>/environments`) and global ones (`~/.openrest/environments`).
 * Secret values never touch the YAML; they live in AppStore keyed by [scope].
 */
object EnvStore {
    /** Scope key used for secrets of global environments. */
    val GLOBAL_SCOPE: Path = Paths.get("<global>")

    fun load(envDir: Path, scope: Path): List<Environment> {
        if (!envDir.isDirectory()) return emptyList()
        return envDir.listDirectoryEntries().filter { it.extension == "yaml" || it.extension == "yml" }.sortedBy { it.name.lowercase() }.mapNotNull { f ->
            val m = runCatching { Yml.parse(f.readText()) as? Map<*, *> }.getOrNull() ?: return@mapNotNull null
            val e = OrYaml.mapToEnv(m, (m["name"] as? String)?.takeIf { it.isNotBlank() } ?: f.nameWithoutExtension)
            e.copy(vars = e.vars.map { v -> if (v.name in e.secrets) v.copy(value = AppStore.getSecret(scope, e.name, v.name)) else v })
        }
    }

    fun save(envDir: Path, scope: Path, env: Environment, oldName: String? = null) {
        envDir.createDirectories()
        val file = envDir.resolve("${Collections.sanitize(env.name)}.yaml")
        if (oldName != null && oldName != env.name) {
            require(!file.exists()) { "An environment named '${env.name}' already exists" }
            envDir.resolve("${Collections.sanitize(oldName)}.yaml").deleteIfExists()
            AppStore.removeSecrets(scope, oldName)
        }
        env.secrets.forEach { s -> AppStore.setSecret(scope, env.name, s, env.vars.firstOrNull { it.name == s }?.value ?: "") }
        file.writeText(Yml.dump(OrYaml.envToMap(env)))
    }

    fun delete(envDir: Path, scope: Path, name: String) {
        envDir.resolve("${Collections.sanitize(name)}.yaml").deleteIfExists()
        AppStore.removeSecrets(scope, name)
    }
}
