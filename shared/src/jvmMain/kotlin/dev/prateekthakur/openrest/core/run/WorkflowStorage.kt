package dev.prateekthakur.openrest.core.run

import dev.prateekthakur.openrest.components.*
import dev.prateekthakur.openrest.core.http.*
import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.model.Collection
import dev.prateekthakur.openrest.core.storage.*
import dev.prateekthakur.openrest.utils.*

import java.nio.file.Path
import kotlin.io.path.*

object WorkflowIO {
    private fun s(v: Any?) = v?.toString() ?: ""
    private fun maps(v: Any?) = (v as? List<*>)?.mapNotNull { it as? Map<*, *> } ?: emptyList()

    private fun stepToMap(st: WorkflowStep): Map<String, Any?> {
        val c = Yml.m(
            "id" to st.id,
            "kind" to (if (st.kind == StepKind.Request) null else st.kind.id),
            "title" to st.title,
            "enabled" to (if (st.enabled) null else false),
        )
        when (st.kind) {
            StepKind.Request -> c.putAll(Yml.m(
                "request" to st.request,
                "continueOnFail" to (if (st.continueOnFail) true else null),
                "inputs" to st.inputs.map { i -> Yml.m("target" to (if (i.target == InputTarget.Variable) null else i.target.id), "name" to i.name, "value" to i.value) },
                "outputs" to st.outputs.map { o -> Yml.m("name" to o.name, "from" to o.kind.id, "path" to o.path) },
            ))
            StepKind.If -> c.putAll(Yml.m(
                "conditions" to st.conditions.map(::condToMap), "match" to (if (st.matchAll) null else "any"),
                "then" to st.children.map(::stepToMap), "else" to st.otherwise.map(::stepToMap),
            ))
            StepKind.Loop -> c.putAll(Yml.m(
                "mode" to st.loop.id, "list" to st.list, "count" to st.count, "item" to st.itemVar, "index" to st.indexVar,
                "max" to (if (st.maxIterations == 100) null else st.maxIterations.toLong()),
                "conditions" to st.conditions.map(::condToMap), "match" to (if (st.matchAll) null else "any"),
                "do" to st.children.map(::stepToMap),
            ))
            StepKind.Set -> c.putAll(Yml.m("op" to st.setOp.id, "name" to st.varName, "list" to st.list, "value" to st.value, "path" to st.path))
            StepKind.Wait -> c.putAll(Yml.m("ms" to st.value))
            StepKind.Stop -> c.putAll(Yml.m("scope" to st.stopScope.id, "fail" to (if (st.stopFail) true else null), "message" to st.value))
        }
        return c
    }

    private fun condToMap(c: Condition) = Yml.m("left" to c.left, "op" to c.op.id, "right" to c.right)
    private fun condFrom(m: Map<*, *>) = Condition(s(m["left"]), CondOp.of(s(m["op"])), s(m["right"]))

    private fun stepFrom(st: Map<*, *>, idx: Int): WorkflowStep {
        val kind = StepKind.of(s(st["kind"]).ifBlank { "request" })
        val base = WorkflowStep(
            id = s(st["id"]).ifBlank { "s${idx + 1}" }, kind = kind, title = s(st["title"]), enabled = st["enabled"] != false,
        )
        fun kids(key: String) = maps(st[key]).mapIndexed { i, m -> stepFrom(m, i) }
        return when (kind) {
            StepKind.Request -> base.copy(
                request = s(st["request"]), continueOnFail = st["continueOnFail"] == true,
                outputs = maps(st["outputs"]).map { o -> StepOutput.of(s(o["name"]), s(o["from"]), s(o["path"])) },
                inputs = maps(st["inputs"]).map { i ->
                    // older files stored (from, source, path); turn those into the equivalent reference
                    val legacy = s(i["from"]).takeIf { it.isNotBlank() }?.let { f ->
                        val src = s(i["source"]).ifBlank { "body" }.let { if (it == "field") "body" else it }; val path = s(i["path"])
                        "{{$f.$src" + (if (path.isNotBlank()) ".$path" else "") + "}}"
                    }
                    StepInput(InputTarget.of(s(i["target"])), s(i["name"]), if (i.containsKey("value")) s(i["value"]) else legacy ?: "")
                },
            )
            StepKind.If -> base.copy(conditions = maps(st["conditions"]).map(::condFrom), matchAll = s(st["match"]) != "any", children = kids("then"), otherwise = kids("else"))
            StepKind.Loop -> base.copy(
                loop = LoopMode.of(s(st["mode"])), list = s(st["list"]), count = s(st["count"]),
                itemVar = s(st["item"]).ifBlank { "item" }, indexVar = s(st["index"]).ifBlank { "index" },
                maxIterations = (st["max"] as? Number)?.toInt() ?: 100,
                conditions = maps(st["conditions"]).map(::condFrom), matchAll = s(st["match"]) != "any", children = kids("do"),
            )
            StepKind.Set -> base.copy(setOp = SetOp.of(s(st["op"])), varName = s(st["name"]), list = s(st["list"]), value = s(st["value"]), path = s(st["path"]))
            StepKind.Wait -> base.copy(value = s(st["ms"]))
            StepKind.Stop -> base.copy(stopScope = StopScope.of(s(st["scope"])), stopFail = st["fail"] == true, value = s(st["message"]))
        }
    }

    fun toMap(w: Workflow): Map<String, Any?> = Yml.m(
        "name" to w.name,
        "description" to w.description,
        "stopOnFail" to (if (w.stopOnFail) null else false),
        "steps" to w.steps.map(::stepToMap),
    )

    fun fromMap(m: Map<*, *>, fallbackName: String): Workflow =
        Workflow(s(m["name"]).ifBlank { fallbackName }, maps(m["steps"]).mapIndexed { i, st -> stepFrom(st, i) }, m["stopOnFail"] != false, s(m["description"]))
}

/** One YAML file per workflow, in the collection's `workflows` folder. */
object WorkflowStore {
    fun dir(col: Path): Path = col.resolve("workflows")
    fun file(col: Path, name: String): Path = dir(col).resolve(Collections.sanitize(name) + ".yaml")

    fun load(col: Path): List<Workflow> {
        val d = dir(col)
        if (!d.isDirectory()) return emptyList()
        return d.listDirectoryEntries().filter { it.extension == "yaml" || it.extension == "yml" }.sortedBy { it.name.lowercase() }.mapNotNull { f ->
            val m = runCatching { Yml.parse(f.readText()) as? Map<*, *> }.getOrNull() ?: return@mapNotNull null
            WorkflowIO.fromMap(m, f.nameWithoutExtension)
        }
    }

    fun save(col: Path, w: Workflow, oldName: String? = null) {
        dir(col).createDirectories()
        val target = file(col, w.name)
        if (oldName != null && oldName != w.name) {
            require(!target.exists()) { "A workflow named '${w.name}' already exists" }
            file(col, oldName).deleteIfExists()
        }
        target.writeText(Yml.dump(WorkflowIO.toMap(w)))
    }

    fun delete(col: Path, name: String) { file(col, name).deleteIfExists() }
}
