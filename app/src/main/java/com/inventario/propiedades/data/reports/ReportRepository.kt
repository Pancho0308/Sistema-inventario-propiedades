package com.inventario.propiedades.data.reports

import android.graphics.Bitmap
import androidx.room.withTransaction
import com.inventario.propiedades.data.PhotoStorage
import com.inventario.propiedades.data.local.InventoryDatabase
import java.io.IOException
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ReportRepository(
    private val database: InventoryDatabase,
    private val photos: PhotoStorage,
    val store: ReportStore,
) {
    private val mutex = Mutex()

    suspend fun readSource(propertyId: Long): ReportSource = withContext(Dispatchers.IO) {
        database.withTransaction {
            val property = checkNotNull(database.propertyDao().getById(propertyId)) { "La propiedad ya no existe" }
            val rooms = database.roomDao().getByProperty(propertyId).map { room ->
                ReportSourceRoom(room.id, room.name, database.itemDao().getByRoom(room.id))
            }
            ReportSource(property, rooms)
        }
    }

    suspend fun prepare(source: ReportSource, onProgress: (ReportProgress) -> Unit = {}): SavedReport = withContext(Dispatchers.IO) {
        mutex.withLock {
            val draft = SavedReport(UUID.randomUUID().toString(), source.property.id,
                source.property.name, source.property.address, todayForReport(), emptyList())
            var committed = false
            try {
                val total = source.rooms.sumOf { it.items.size }
                var completed = 0
                val rooms = source.rooms.map { room ->
                    val items = room.items.map { item ->
                        currentCoroutineContext().ensureActive()
                        val photoName = if (item.photoPath != null) "item-${item.id}.jpg" else null
                        val copied = photoName != null && copyThumbnail(item.photoPath!!, draft, photoName)
                        completed++
                        onProgress(ReportProgress("Preparando artículos", completed, total))
                        ReportItem(item.id, item.name, item.status, item.note,
                            if (copied) photoName else null, item.photoPath != null && !copied)
                    }
                    ReportRoom(room.id, room.name, items)
                }
                currentCoroutineContext().ensureActive()
                val report = draft.copy(rooms = rooms)
                store.write(report)
                committed = true
                report
            } finally {
                if (!committed) withContext(NonCancellable) { store.delete(draft.propertyId, draft.id) }
            }
        }
    }

    private fun copyThumbnail(path: String, report: SavedReport, name: String): Boolean {
        val original = try { photos.load(path, 256) } catch (_: IOException) { null } ?: return false
        var thumbnail = original
        try {
            val scale = minOf(1f, 256f / maxOf(original.width, original.height))
            thumbnail = Bitmap.createScaledBitmap(original,
                (original.width * scale).roundToInt().coerceAtLeast(1),
                (original.height * scale).roundToInt().coerceAtLeast(1), true)
            store.writeAtomic(store.photoFile(report, name)) {
                check(thumbnail.compress(Bitmap.CompressFormat.JPEG, 80, it)) { "No se pudo preparar una foto" }
            }
            return true
        } finally {
            if (thumbnail !== original) thumbnail.recycle()
            original.recycle()
        }
    }

    suspend fun get(propertyId: Long, id: String): SavedReport = withContext(Dispatchers.IO) { store.read(propertyId, id) }
    suspend fun list(propertyId: Long): List<ReportEntry> = withContext(Dispatchers.IO) { store.list(propertyId) }

    suspend fun saveSignature(propertyId: Long, id: String, role: SignerRole, name: String, bitmap: Bitmap): SavedReport =
        withContext(Dispatchers.IO) { mutex.withLock { store.saveSignature(store.read(propertyId, id), role, name, bitmap) } }

    suspend fun delete(propertyId: Long, id: String) = withContext(Dispatchers.IO + NonCancellable) {
        mutex.withLock { store.delete(propertyId, id) }
    }
}
