package com.inventario.propiedades.data.reports

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.text.Normalizer
import java.util.UUID

class ReportStore(filesDir: File) {
    private val internalDir = filesDir.canonicalFile

    private fun checked(vararg parts: String): File {
        var file = internalDir
        for (part in parts) {
            require(part.isNotEmpty() && part != "." && part != ".." && File(part).name == part && '\\' !in part)
            file = File(file, part)
            require(file.canonicalFile == file.absoluteFile) { "Ruta de informe no válida" }
        }
        return file
    }

    private fun validateIds(propertyId: Long, id: String) {
        require(propertyId > 0 && UUID.fromString(id).toString() == id) { "Identificador de informe no válido" }
    }

    fun dataDirectory(propertyId: Long, id: String): File {
        validateIds(propertyId, id)
        return checked("reports", "data", propertyId.toString(), id)
    }

    private fun assetFile(report: SavedReport, folder: String, name: String): File {
        require(name.matches(Regex("[A-Za-z0-9_-]+\\.(jpg|png)"))) { "Nombre de imagen no válido" }
        validateIds(report.propertyId, report.id)
        return checked("reports", "data", report.propertyId.toString(), report.id, folder, name)
    }

    fun photoFile(report: SavedReport, name: String) = assetFile(report, "photos", name)
    fun signatureFile(report: SavedReport, name: String) = assetFile(report, "signatures", name)

    fun writeAtomic(file: File, write: (FileOutputStream) -> Unit) {
        require(file.canonicalFile == file.absoluteFile) { "Archivo de informe no válido" }
        require(file.toPath().startsWith(File(internalDir, "reports").toPath())) { "Archivo fuera de la carpeta de informes" }
        val parent = checkNotNull(file.parentFile)
        check(parent.mkdirs() || parent.isDirectory) { "No se pudo crear la carpeta del informe" }
        val temporary = File(parent, "${file.name}.${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(temporary).use { output -> write(output); output.fd.sync() }
            if (!temporary.renameTo(file)) throw IOException("No se pudo confirmar el archivo del informe")
        } finally { temporary.delete() }
    }

    private fun metadataFile(propertyId: Long, id: String): File {
        validateIds(propertyId, id)
        return checked("reports", "data", propertyId.toString(), id, "metadata.json")
    }

    fun write(report: SavedReport) {
        val file = metadataFile(report.propertyId, report.id)
        writeAtomic(file) { it.write(ReportJson.encode(report).toByteArray(Charsets.UTF_8)) }
    }

    fun read(propertyId: Long, id: String): SavedReport {
        val report = ReportJson.decode(metadataFile(propertyId, id).readText(Charsets.UTF_8))
        require(report.id == id && report.propertyId == propertyId) { "El informe no corresponde a esta propiedad" }
        for (room in report.rooms) for (item in room.items) item.photoName?.let { photoFile(report, it) }
        for (signature in report.signatures) signatureFile(report, signature.fileName)
        for (pdf in report.pdfFiles) pdfFile(report, pdf)
        return if (report.pdfUpToDate && !pdfFile(report, report.pdfFiles.last()).isFile) report.copy(pdfUpToDate = false) else report
    }

    fun list(propertyId: Long): List<ReportEntry> {
        require(propertyId > 0)
        val parent = checked("reports", "data", propertyId.toString())
        if (!parent.exists()) return emptyList()
        val folders = parent.listFiles() ?: throw IOException("No se pudo leer la lista de informes")
        return folders.filter { it.isDirectory && runCatching { UUID.fromString(it.name).toString() == it.name }.getOrDefault(false) }
            .sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.name })
            .map { folder ->
                val report = try { read(propertyId, folder.name) } catch (_: Exception) { null }
                ReportEntry(propertyId, folder.name, report)
            }
    }

    fun pdfName(report: SavedReport, signed: Boolean = report.signatures.isNotEmpty()): String {
        val cleanName = Normalizer.normalize(report.propertyName, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace(Regex("[^A-Za-z0-9]+"), "_")
            .trim('_').take(60).ifBlank { "Propiedad" }
        return "Inventario_${cleanName}_${report.date}${if (signed) "_firmado" else ""}.pdf"
    }

    fun newPdf(report: SavedReport): PdfDestination {
        validateIds(report.propertyId, report.id)
        val revision = UUID.randomUUID().toString()
        val name = pdfName(report)
        val file = checked("reports", "pdfs", report.propertyId.toString(), report.id, revision, name)
        return PdfDestination(file, "$revision/$name")
    }

    fun pdfFile(report: SavedReport, relativePath: String): File {
        validateIds(report.propertyId, report.id)
        val parts = relativePath.split('/')
        require(parts.size == 2 && UUID.fromString(parts[0]).toString() == parts[0]) { "Versión de PDF no válida" }
        require(parts[1] == pdfName(report, false) || parts[1] == pdfName(report, true)) { "Nombre de PDF no válido" }
        return checked("reports", "pdfs", report.propertyId.toString(), report.id, parts[0], parts[1])
    }

    fun saveSignature(report: SavedReport, role: SignerRole, name: String, bitmap: Bitmap): SavedReport {
        require(name.isNotBlank()) { "Escribe el nombre del firmante" }
        val fileName = "${UUID.randomUUID()}.png"
        val file = signatureFile(report, fileName)
        val updated = report.copy(signatures = report.signatures.filter { it.role != role } +
            ReportSignature(role, name.trim(), todayForReport(), fileName), pdfUpToDate = false)
        try {
            writeAtomic(file) { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "No se pudo guardar la firma" } }
            write(updated)
        } catch (error: Exception) {
            file.delete()
            throw error
        }
        report.signatures.firstOrNull { it.role == role }?.let { signatureFile(report, it.fileName).delete() }
        return updated
    }

    fun loadPhoto(report: SavedReport, name: String): Bitmap? = decode(photoFile(report, name), 256)
    fun loadSignature(report: SavedReport, name: String): Bitmap? = decode(signatureFile(report, name), 512)

    private fun decode(file: File, maxSide: Int): Bitmap? {
        if (!file.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply { inSampleSize = 1 }
        while (maxOf(bounds.outWidth, bounds.outHeight) / (options.inSampleSize * 2) >= maxSide) options.inSampleSize *= 2
        return BitmapFactory.decodeFile(file.path, options)
    }

    fun delete(propertyId: Long, id: String) {
        validateIds(propertyId, id)
        // No sigue enlaces simbólicos: eliminar un informe nunca puede recorrer archivos de otro.
        for (kind in listOf("pdfs", "data")) {
            val parent = checked("reports", kind, propertyId.toString())
            val path = File(parent, id).toPath()
            if (!Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) continue
            Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                    Files.delete(file)
                    return FileVisitResult.CONTINUE
                }
                override fun postVisitDirectory(directory: Path, error: IOException?): FileVisitResult {
                    if (error != null) throw error
                    Files.delete(directory)
                    return FileVisitResult.CONTINUE
                }
            })
        }
    }
}
