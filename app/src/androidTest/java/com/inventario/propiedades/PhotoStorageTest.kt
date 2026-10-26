package com.inventario.propiedades

import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.system.Os
import java.io.File
import java.io.IOException

class PhotoStorageTest : DatabaseTestCase() {
    fun testResizesTo1600AndLoadsSampledThumbnail() {
        val capture = createCapture(3200, 2400)
        val photo = photoStorage.saveCapture(capture)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(photo.path, bounds)
        assertEquals(1600, bounds.outWidth)
        assertEquals(1200, bounds.outHeight)
        assertEquals("image/jpeg", bounds.outMimeType)
        assertTrue(photo.length() < capture.length())
        val thumbnail = photoStorage.load(photo.path, 128)!!
        try { assertTrue(maxOf(thumbnail.width, thumbnail.height) < 256) } finally { thumbnail.recycle() }
    }

    fun testAppliesExifRotationWithoutUpscaling() {
        val capture = createCapture(300, 600, ExifInterface.ORIENTATION_ROTATE_90)
        val photo = photoStorage.saveCapture(capture)
        val bitmap = photoStorage.load(photo.path, 1600)!!
        try {
            assertEquals(600, bitmap.width)
            assertEquals(300, bitmap.height)
        } finally { bitmap.recycle() }
    }

    fun testCorruptCaptureDoesNotCreateFinalPhoto() {
        val capture = photoStorage.createCaptureFile().apply { writeText("No es una foto") }
        try {
            photoStorage.saveCapture(capture)
            fail("Debe rechazar una captura inválida")
        } catch (_: IOException) {
            assertEquals(listOf(capture.name), photoStorage.directory.listFiles()!!.map { it.name })
        }
    }

    fun testNeverDeletesOrLoadsOutsideItsOwnDirectory() {
        val capture = createCapture()
        val outside = File(photoStorage.directory.parentFile, "outside.jpg").apply { writeBytes(capture.readBytes()) }
        photoStorage.delete(outside.path)
        photoStorage.delete(File(photoStorage.directory, "../outside.jpg").path)
        assertTrue(outside.exists())
        assertNull(photoStorage.load(outside.path, 128))
        val link = File(photoStorage.directory, "linked.jpg")
        Os.symlink(outside.path, link.path)
        photoStorage.delete(link.path)
        assertTrue(outside.exists())
        photoStorage.delete(capture.path)
        assertFalse(capture.exists())
    }

    fun testMissingFileIsSafeAndDirectoriesAreNeverDeleted() {
        val capture = createCapture()
        photoStorage.delete(capture.path)
        photoStorage.delete(capture.path)
        val folder = File(photoStorage.directory, "folder").apply { mkdir() }
        try {
            photoStorage.delete(folder.path)
            fail("No debe borrar un directorio")
        } catch (_: IOException) { assertTrue(folder.isDirectory) }
    }
}
