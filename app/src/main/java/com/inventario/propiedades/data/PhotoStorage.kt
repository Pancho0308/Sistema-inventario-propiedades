package com.inventario.propiedades.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlin.math.roundToInt

class PhotoStorage(filesDir: File) {
    private val internalDir = filesDir.canonicalFile
    val directory = File(internalDir, "photos")

    private fun ownedFile(path: String): File? {
        val root = directory.canonicalFile
        if (root.parentFile != internalDir) return null
        return File(path).canonicalFile.takeIf { it.parentFile == root }
    }

    fun createCaptureFile(): File {
        check(directory.mkdirs() || directory.isDirectory) { "No se pudo crear la carpeta de fotos" }
        check(directory.canonicalFile.parentFile == internalDir) { "Carpeta de fotos no válida" }
        return File.createTempFile("capture-", ".jpg", directory)
    }

    fun saveCapture(capture: File): File {
        require(ownedFile(capture.path) != null) { "La captura no pertenece a la app" }
        val decoded = decode(capture.path, 1600) ?: throw IOException("La captura no es una imagen válida")
        var oriented = decoded
        var scaled = decoded
        val output = File(directory, "${UUID.randomUUID()}.jpg")
        try {
            val orientation = ExifInterface(capture.path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
            )
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
                }
            }
            oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            val ratio = minOf(1f, 1600f / maxOf(oriented.width, oriented.height))
            scaled = Bitmap.createScaledBitmap(oriented,
                (oriented.width * ratio).roundToInt().coerceAtLeast(1),
                (oriented.height * ratio).roundToInt().coerceAtLeast(1), true)
            FileOutputStream(output).use { stream ->
                check(scaled.compress(Bitmap.CompressFormat.JPEG, 80, stream)) { "No se pudo guardar la foto" }
                stream.fd.sync()
            }
            return output
        } catch (error: Exception) {
            delete(output.path)
            throw error
        } finally {
            if (scaled !== oriented && scaled !== decoded) scaled.recycle()
            if (oriented !== decoded) oriented.recycle()
            decoded.recycle()
        }
    }

    fun load(path: String, maxSide: Int): Bitmap? {
        val file = ownedFile(path) ?: return null
        return if (file.isFile) decode(file.path, maxSide) else null
    }

    private fun decode(path: String, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply { inSampleSize = 1 }
        while (maxOf(bounds.outWidth, bounds.outHeight) / (options.inSampleSize * 2) >= maxSide) {
            options.inSampleSize *= 2
        }
        return BitmapFactory.decodeFile(path, options)
    }

    fun delete(path: String?) {
        if (path == null) return
        val file = ownedFile(path) ?: return
        // Nunca borra directorios ni rutas externas, aunque hayan quedado guardadas en Room.
        if (file.exists() && (!file.isFile || !file.delete())) throw IOException("No se pudo eliminar el archivo de foto")
    }

    fun deleteAll(paths: List<String>) {
        var failure: IOException? = null
        paths.distinct().forEach { path ->
            try { delete(path) } catch (error: IOException) { failure = error }
        }
        failure?.let { throw it }
    }
}
