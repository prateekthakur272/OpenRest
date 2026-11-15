package dev.prateekthakur.openrest.core.run

import dev.prateekthakur.openrest.components.*
import dev.prateekthakur.openrest.core.http.*
import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.model.Collection
import dev.prateekthakur.openrest.core.storage.*
import dev.prateekthakur.openrest.utils.*

import java.nio.file.Path
import kotlin.io.path.*

/** Depth-first, in execution order (a block, then its children, then its else branch). */
fun List<WorkflowStep>.walk(): List<WorkflowStep> = flatMap { listOf(it) + it.children.walk() + it.otherwise.walk() }

fun List<WorkflowStep>.findStep(id: String): WorkflowStep? = walk().firstOrNull { it.id == id }

fun List<WorkflowStep>.mapStep(id: String, f: (WorkflowStep) -> WorkflowStep): List<WorkflowStep> =
    map { if (it.id == id) f(it) else it.copy(children = it.children.mapStep(id, f), otherwise = it.otherwise.mapStep(id, f)) }

fun List<WorkflowStep>.removeStep(id: String): List<WorkflowStep> =
    filter { it.id != id }.map { it.copy(children = it.children.removeStep(id), otherwise = it.otherwise.removeStep(id)) }

/** Appends [step] to the top level ([parentId] null) or to a block's main branch ([branch] 0) / else branch (1). */
fun List<WorkflowStep>.addStep(parentId: String?, branch: Int, step: WorkflowStep): List<WorkflowStep> =
    if (parentId == null) this + step
    else mapStep(parentId) { if (branch == 1) it.copy(otherwise = it.otherwise + step) else it.copy(children = it.children + step) }

/** Moves a block up/down among its siblings. */
fun List<WorkflowStep>.moveStep(id: String, delta: Int): List<WorkflowStep> {
    val i = indexOfFirst { it.id == id }
    if (i >= 0) {
        val j = i + delta
        return if (j !in indices) this else toMutableList().also { it.add(j, it.removeAt(i)) }
    }
    return map { it.copy(children = it.children.moveStep(id, delta), otherwise = it.otherwise.moveStep(id, delta)) }
}

fun newStepId(existing: List<WorkflowStep>): String {
    val all = existing.walk()
    var i = all.size + 1
    while (all.any { it.id == "s$i" }) i++
    return "s$i"
}

/** Request blocks that run before [id] (anything else can read their responses). */
fun List<WorkflowStep>.requestsBefore(id: String): List<WorkflowStep> =
    walk().takeWhile { it.id != id }.filter { it.kind == StepKind.Request }

/** Names of variables made by loops and Set blocks that run before [id]. */
fun List<WorkflowStep>.variablesBefore(id: String): List<String> {
    val out = linkedSetOf<String>()
    for (s in walk()) {
        if (s.id == id) break
        if (s.kind == StepKind.Loop) { if (s.itemVar.isNotBlank() && s.loop == LoopMode.ForEach) out += s.itemVar.trim(); if (s.indexVar.isNotBlank()) out += s.indexVar.trim() }
        if (s.kind == StepKind.Set && s.varName.isNotBlank()) out += s.varName.trim()
    }
    // a block can also use the variables of the loops that contain it
    fun enclosing(list: List<WorkflowStep>, trail: List<WorkflowStep>): List<WorkflowStep>? {
        for (s in list) {
            if (s.id == id) return trail
            enclosing(s.children, trail + s)?.let { return it }
            enclosing(s.otherwise, trail + s)?.let { return it }
        }
        return null
    }
    enclosing(this, emptyList())?.filter { it.kind == StepKind.Loop }?.forEach { l ->
        if (l.itemVar.isNotBlank() && l.loop == LoopMode.ForEach) out += l.itemVar.trim(); if (l.indexVar.isNotBlank()) out += l.indexVar.trim()
    }
    return out.toList()
}
