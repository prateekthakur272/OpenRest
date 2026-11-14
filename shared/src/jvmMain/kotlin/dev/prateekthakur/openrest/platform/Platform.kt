package dev.prateekthakur.openrest.platform

import androidx.compose.runtime.*
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JFileChooser
import kotlin.io.path.*

object Platform {
    val isMac = System.getProperty("os.name").lowercase().contains("mac")
    private val mac get() = isMac

    fun pickDirectory(title: String): Path? = try {
        if (mac) {
            System.setProperty("apple.awt.fileDialogForDirectories", "true")
            val d = FileDialog(null as Frame?, title, FileDialog.LOAD)
            d.isVisible = true
            System.setProperty("apple.awt.fileDialogForDirectories", "false")
            if (d.file != null) Paths.get(d.directory, d.file) else null
        } else {
            val c = JFileChooser().apply { dialogTitle = title; fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }
            if (c.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) c.selectedFile.toPath() else null
        }
    } catch (_: Exception) { null }

    fun pickFile(title: String): Path? = try {
        if (mac) {
            val d = FileDialog(null as Frame?, title, FileDialog.LOAD)
            d.isVisible = true
            if (d.file != null) Paths.get(d.directory, d.file) else null
        } else {
            val c = JFileChooser().apply { dialogTitle = title }
            if (c.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) c.selectedFile.toPath() else null
        }
    } catch (_: Exception) { null }

    fun saveFile(title: String, name: String): Path? = try {
        val d = FileDialog(null as Frame?, title, FileDialog.SAVE)
        d.file = name
        d.isVisible = true
        if (d.file != null) Paths.get(d.directory, d.file) else null
    } catch (_: Exception) { null }

    fun reveal(p: Path) {
        runCatching { if (Desktop.isDesktopSupported()) Desktop.getDesktop().open((if (p.isDirectory()) p else p.parent).toFile()) }
    }

    fun openBytes(bytes: ByteArray, ext: String, browser: Boolean = false) {
        runCatching {
            val f = java.nio.file.Files.createTempFile("openrest-", ext).also { it.toFile().deleteOnExit() }
            java.nio.file.Files.write(f, bytes)
            if (Desktop.isDesktopSupported()) { if (browser) Desktop.getDesktop().browse(f.toUri()) else Desktop.getDesktop().open(f.toFile()) }
        }
    }

    fun copy(text: String) {
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
    }

    fun paste(): String = runCatching { Toolkit.getDefaultToolkit().systemClipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as String }.getOrDefault("")
}
