package com.inventario.propiedades.data

import androidx.room.withTransaction
import com.inventario.propiedades.data.local.InventoryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import com.inventario.propiedades.data.local.RoomDao
import com.inventario.propiedades.data.local.RoomEntity

class RoomRepository(
    private val database: InventoryDatabase,
    private val photos: PhotoStorage,
    private val roomDao: RoomDao = database.roomDao(),
) {
    fun observeRooms(propertyId: Long) = roomDao.observeWithProgress(propertyId)

    suspend fun create(propertyId: Long, name: String): Long {
        require(name.isNotBlank()) { "El nombre es obligatorio" }
        return roomDao.insert(RoomEntity(propertyId = propertyId, name = name.trim()))
    }

    suspend fun delete(roomId: Long) = withContext(Dispatchers.IO + NonCancellable) {
        val paths = database.withTransaction {
            val room = roomDao.getById(roomId) ?: return@withTransaction emptyList()
            val paths = database.itemDao().getPhotoPathsByRoom(roomId)
            check(roomDao.delete(room) == 1)
            paths
        }
        photos.deleteAll(paths)
    }
}
