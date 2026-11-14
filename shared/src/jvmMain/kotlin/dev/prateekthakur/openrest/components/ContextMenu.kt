package dev.prateekthakur.openrest.components

import dev.prateekthakur.openrest.core.http.*
import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.model.Collection
import dev.prateekthakur.openrest.core.run.*
import dev.prateekthakur.openrest.theme.*

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp

private fun menuIcon(l: String) = when {
    l.startsWith("New Request") || l.startsWith("New Collection") -> Ic.NewFile
    l.startsWith("New Folder") -> Ic.NewFolder
    l.startsWith("Open") -> Ic.Open
    l.startsWith("Import") -> Ic.Import
    l.startsWith("Export") -> Ic.Export
    l.startsWith("New Workflow") -> Ic.Workflow
    l.startsWith("Run") -> Ic.Play
    l.startsWith("Environments") -> Ic.Environments
    l.startsWith("Settings") -> Ic.Settings
    l.startsWith("Variables") -> Ic.Variables
    l.startsWith("Rename") -> Ic.Rename
    l.startsWith("Clone") -> Ic.Clone
    l.startsWith("Show in Folder") -> Ic.Reveal
    l.startsWith("Reload") -> Ic.Refresh
    l.startsWith("Close Collection") || l.startsWith("Close") -> Ic.Close
    l.startsWith("Delete") -> Ic.Delete
    l.startsWith("Move Up") -> Ic.MoveUp
    l.startsWith("Move Down") -> Ic.MoveDown
    l.startsWith("Send") -> Ic.Send
    l.startsWith("Save") -> Ic.Save
    l.startsWith("Copy") -> Ic.Copy
    l.startsWith("Generate") -> Ic.Code
    l.startsWith("Decode") || l.startsWith("Encode") -> Ic.Codec
    else -> Ic.Command
}

class MenuItem(val label: String?, val danger: Boolean = false, val action: () -> Unit = {})

@Composable
fun ContextMenu(open: Boolean, onDismiss: () -> Unit, items: List<MenuItem>) {
    val p = LocalPal.current
    DropdownMenu(expanded = open, onDismissRequest = onDismiss, containerColor = p.panel) {
        items.forEach { it0 ->
            if (it0.label == null) Box(Modifier.fillMaxWidth().padding(vertical = 3.dp).height(1.dp).background(p.border))
            else DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Ico(menuIcon(it0.label), tint = if (it0.danger) p.err else p.muted, size = 16.dp)
                        T(it0.label, color = if (it0.danger) p.err else p.text)
                    }
                },
                onClick = { onDismiss(); it0.action() },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
            )
        }
    }
}
