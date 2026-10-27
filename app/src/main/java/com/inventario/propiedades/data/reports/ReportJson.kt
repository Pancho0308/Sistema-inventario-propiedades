package com.inventario.propiedades.data.reports

import com.inventario.propiedades.data.local.ItemStatus
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

internal object ReportJson {
    const val SCHEMA_VERSION = 1

    fun encode(report: SavedReport): String {
        val rooms = JSONArray()
        for (room in report.rooms) {
            val items = JSONArray()
            for (item in room.items) items.put(JSONObject()
                .put("id", item.id).put("name", item.name)
                .put("status", item.status?.name ?: JSONObject.NULL).put("note", item.note)
                .put("photoName", item.photoName ?: JSONObject.NULL).put("photoUnavailable", item.photoUnavailable))
            rooms.put(JSONObject().put("id", room.id).put("name", room.name).put("items", items))
        }
        val signatures = JSONArray()
        for (signature in report.signatures) signatures.put(JSONObject()
            .put("role", signature.role.name).put("name", signature.name)
            .put("date", signature.date).put("fileName", signature.fileName))
        return JSONObject().put("schemaVersion", SCHEMA_VERSION)
            .put("id", report.id).put("propertyId", report.propertyId)
            .put("propertyName", report.propertyName).put("address", report.address).put("date", report.date)
            .put("rooms", rooms).put("signatures", signatures)
            .put("pdfFiles", JSONArray(report.pdfFiles)).put("pdfUpToDate", report.pdfUpToDate).toString()
    }

    fun decode(text: String): SavedReport {
        val json = JSONObject(text)
        require(json.getInt("schemaVersion") == SCHEMA_VERSION) { "Versión de informe no compatible" }
        val rooms = json.getJSONArray("rooms")
        val signatures = json.getJSONArray("signatures")
        val pdfs = json.getJSONArray("pdfFiles")
        val report = SavedReport(
            id = json.getString("id"), propertyId = json.getLong("propertyId"),
            propertyName = json.getString("propertyName"), address = json.getString("address"), date = json.getString("date"),
            rooms = List(rooms.length()) { index ->
                val room = rooms.getJSONObject(index)
                val items = room.getJSONArray("items")
                ReportRoom(room.getLong("id"), room.getString("name"), List(items.length()) { itemIndex ->
                    val item = items.getJSONObject(itemIndex)
                    ReportItem(item.getLong("id"), item.getString("name"),
                        if (item.isNull("status")) null else ItemStatus.valueOf(item.getString("status")),
                        item.getString("note"), if (item.isNull("photoName")) null else item.getString("photoName"),
                        item.getBoolean("photoUnavailable"))
                })
            },
            signatures = List(signatures.length()) { index ->
                val signature = signatures.getJSONObject(index)
                ReportSignature(SignerRole.valueOf(signature.getString("role")), signature.getString("name"),
                    signature.getString("date"), signature.getString("fileName"))
            },
            pdfFiles = List(pdfs.length()) { pdfs.getString(it) }, pdfUpToDate = json.getBoolean("pdfUpToDate"),
        )
        require(report.propertyName.isNotBlank() && report.address.isNotBlank()) { "Encabezado inválido" }
        LocalDate.parse(report.date, reportDateFormat)
        require(report.signatures.map { it.role }.distinct().size == report.signatures.size) { "Firmas duplicadas" }
        for (signature in report.signatures) {
            require(signature.name.isNotBlank()) { "Firmante sin nombre" }
            LocalDate.parse(signature.date, reportDateFormat)
        }
        require(!report.pdfUpToDate || report.pdfFiles.isNotEmpty()) { "Informe sin PDF" }
        return report
    }
}
