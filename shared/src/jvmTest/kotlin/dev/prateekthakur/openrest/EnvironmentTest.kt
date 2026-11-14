package dev.prateekthakur.openrest

import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.run.*
import dev.prateekthakur.openrest.core.storage.*

import kotlin.io.path.*
import kotlin.test.*

class EnvironmentTest {
    private fun execution(global: Environment?, env: Environment?, collVars: List<KV> = emptyList()): Execution {
        val tmp = createTempDirectory("or-env")
        val dir = Collections.create(tmp, "C")
        Collections.saveCollectionConfig(dir, NodeConfig(name = "C", preVars = collVars))
        Collections.createRequest(dir, ApiRequest(name = "R", url = "http://localhost:1/{{host}}/{{only_global}}"))
        env?.let { Collections.saveEnvironment(dir, it) }
        val col = Collections.load(dir)
        return Execution(RunCtx(col, env?.name, mutableMapOf(), AppSettings(), global), col.allRequests()[0])
    }

    @Test fun globalIsFallback() {
        val g = Environment("G", listOf(KV("host", "global-host"), KV("only_global", "g")))
        val x = execution(g, null)
        assertEquals("global-host", x.lookup("host"))
        assertEquals("g", x.lookup("only_global"))
    }

    @Test fun collectionEnvOverridesGlobal() {
        val g = Environment("G", listOf(KV("host", "global-host"), KV("only_global", "g")))
        val x = execution(g, Environment("Dev", listOf(KV("host", "dev-host"))))
        assertEquals("dev-host", x.lookup("host"))
        assertEquals("g", x.lookup("only_global"))
    }

    @Test fun disabledGlobalVarsIgnored() {
        val x = execution(Environment("G", listOf(KV("host", "h", enabled = false))), null)
        assertNull(x.lookup("host"))
    }

    @Test fun envStoreRoundTripRenameDelete() {
        val dir = createTempDirectory("or-genv").resolve("environments")
        val scope = createTempDirectory("scope")
        EnvStore.save(dir, scope, Environment("Prod/EU", listOf(KV("a", "1"))))
        assertEquals(listOf("Prod/EU"), EnvStore.load(dir, scope).map { it.name })
        EnvStore.save(dir, scope, Environment("Prod EU", listOf(KV("a", "1"))), "Prod/EU")
        assertEquals(listOf("Prod EU"), EnvStore.load(dir, scope).map { it.name })
        EnvStore.save(dir, scope, Environment("Other"))
        assertFailsWith<IllegalArgumentException> { EnvStore.save(dir, scope, Environment("Other"), "Prod EU") }
        EnvStore.delete(dir, scope, "Prod EU")
        assertEquals(listOf("Other"), EnvStore.load(dir, scope).map { it.name })
    }
}
