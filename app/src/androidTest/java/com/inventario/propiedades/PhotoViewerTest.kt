@file:Suppress("DEPRECATION")

package com.inventario.propiedades

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import android.test.InstrumentationTestCase
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.inventario.propiedades.data.PhotoStorage
import com.inventario.propiedades.data.local.ItemEntity
import com.inventario.propiedades.ui.photos.PhotoViewer
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class PhotoViewerTest : InstrumentationTestCase() {
    fun testPhotoActionsRemainVisibleAndClickableWithPortraitImage() = verifyPhotoActions(800, 1200)

    fun testPhotoActionsRemainVisibleAndClickableWithLandscapeImage() = verifyPhotoActions(1200, 800)

    private fun verifyPhotoActions(width: Int, height: Int) {
        val context = instrumentation.targetContext
        val files = File(context.cacheDir, "viewer-test-${UUID.randomUUID()}").apply { mkdirs() }
        var activity: MainActivity? = null
        try {
            val photos = PhotoStorage(files)
            val capture = photos.createCaptureFile()
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(Color.BLUE)
                capture.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            } finally { bitmap.recycle() }
            val photo = photos.saveCapture(capture)
            val item = ItemEntity(id = 1, roomId = 1, name = "Prueba del visor", photoPath = photo.path)
            val replaced = AtomicBoolean(false)
            val deleted = AtomicBoolean(false)
            val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
            val opened = try {
                // El celular restringe los inicios de Activity desde segundo plano; usa el inicio normal de adb.
                android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
                    "am start -n ${context.packageName}/.MainActivity",
                )).use { it.readBytes() }
                monitor.waitForActivityWithTimeout(10_000) as? MainActivity
                    ?: throw AssertionError("No se pudo abrir la Activity del visor")
            } finally {
                instrumentation.removeMonitor(monitor)
            }
            activity = opened
            // El visor de prueba usa solo archivos temporales; sus acciones no modifican el inventario.
            instrumentation.runOnMainSync {
                opened.setContent {
                    MaterialTheme {
                        PhotoViewer(item, photos, onDismiss = {},
                            onReplace = { replaced.set(true) }, onDelete = { deleted.set(true) })
                    }
                }
            }
            waitForNode("Foto de Prueba del visor", description = true)
            val windowBounds = Rect()
            instrumentation.runOnMainSync { opened.window.decorView.getWindowVisibleDisplayFrame(windowBounds) }
            for (label in listOf("Reemplazar", "Eliminar foto")) {
                var node = waitForNode(label)
                val bounds = Rect().also { node.getBoundsInScreen(it) }
                assertTrue("$label debe ser visible dentro del visor: $bounds / $windowBounds",
                    node.isVisibleToUser && !bounds.isEmpty && windowBounds.contains(bounds))
                while (!node.isClickable && node.parent != null) node = node.parent
                assertTrue("$label debe responder al toque", node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                instrumentation.waitForIdleSync()
            }
            assertTrue(replaced.get())
            assertTrue(deleted.get())
        } finally {
            activity?.let { opened -> instrumentation.runOnMainSync { opened.finish() } }
            files.deleteRecursively()
        }
    }

    private fun waitForNode(text: String, description: Boolean = false): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            val found = findNode(root, text, description)
            if (found != null) return found
            SystemClock.sleep(50)
        }
        throw AssertionError("No aparece en el visor: $text")
    }

    private fun findNode(node: AccessibilityNodeInfo?, text: String, description: Boolean): AccessibilityNodeInfo? {
        if (node == null) return null
        val label = if (description) node.contentDescription else node.text
        if (label?.toString() == text) return node
        repeat(node.childCount) { index -> findNode(node.getChild(index), text, description)?.let { return it } }
        return null
    }
}
