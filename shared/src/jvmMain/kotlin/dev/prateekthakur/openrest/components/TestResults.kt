package dev.prateekthakur.openrest.components

import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.theme.*

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TestsList(tests: List<TestResult>) {
    val p = LocalPal.current
    Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tests.forEach { t ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Ico(if (t.passed) Ic.CheckCircle else Ic.Cancel, tint = if (t.passed) p.ok else p.err, size = 16.dp)
                Column {
                    T(t.name, mono = true)
                    if (!t.passed && t.message.isNotBlank()) T(t.message, color = p.err, mono = true, size = 12)
                }
            }
        }
    }
}
