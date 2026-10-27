package com.inventario.propiedades.data.reports

import com.inventario.propiedades.data.local.ItemEntity
import com.inventario.propiedades.data.local.ItemStatus
import com.inventario.propiedades.data.local.PropertyEntity
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

val reportDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-uuuu", Locale.ROOT)
    .withResolverStyle(ResolverStyle.STRICT)

fun todayForReport(): String = LocalDate.now().format(reportDateFormat)

enum class SignerRole(val label: String) { BROKER("Corredor"), CLIENT("Cliente") }

data class ReportSourceRoom(val id: Long, val name: String, val items: List<ItemEntity>)
data class ReportSource(val property: PropertyEntity, val rooms: List<ReportSourceRoom>) {
    val unreviewedCount get() = rooms.sumOf { room -> room.items.count { it.status == null } }
    val firstPendingRoomId get() = rooms.firstOrNull { room -> room.items.any { it.status == null } }?.id
}

data class ReportItem(
    val id: Long,
    val name: String,
    val status: ItemStatus?,
    val note: String,
    val photoName: String? = null,
    val photoUnavailable: Boolean = false,
)
data class ReportRoom(val id: Long, val name: String, val items: List<ReportItem>)
data class ReportSignature(val role: SignerRole, val name: String, val date: String, val fileName: String)
data class SavedReport(
    val id: String,
    val propertyId: Long,
    val propertyName: String,
    val address: String,
    val date: String,
    val rooms: List<ReportRoom>,
    val signatures: List<ReportSignature> = emptyList(),
    val pdfFiles: List<String> = emptyList(),
    val pdfUpToDate: Boolean = false,
) {
    val itemCount get() = rooms.sumOf { it.items.size }
    val unreviewedCount get() = rooms.sumOf { room -> room.items.count { it.status == null } }
    fun count(status: ItemStatus) = rooms.sumOf { room -> room.items.count { it.status == status } }
}

data class ReportEntry(val propertyId: Long, val id: String, val report: SavedReport?)
data class ReportProgress(val message: String, val completed: Int = 0, val total: Int = 0)
data class PdfDestination(val file: File, val relativePath: String)
