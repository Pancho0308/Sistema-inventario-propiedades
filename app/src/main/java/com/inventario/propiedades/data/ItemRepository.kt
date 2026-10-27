package com.inventario.propiedades.data

import androidx.room.withTransaction
import com.inventario.propiedades.data.local.InventoryDatabase
import com.inventario.propiedades.data.local.ItemDao
import com.inventario.propiedades.data.local.ItemEntity
import com.inventario.propiedades.data.local.ItemStatus
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class ItemRepository(
    private val database: InventoryDatabase,
    private val photos: PhotoStorage,
    private val itemDao: ItemDao = database.itemDao(),
) {
    fun observeItems(roomId: Long) = itemDao.observeByRoom(roomId)

    suspend fun create(roomId: Long, name: String): Long {
        require(name.isNotBlank()) { "El nombre es obligatorio" }
        return itemDao.insert(ItemEntity(roomId = roomId, name = name.trim()))
    }

    suspend fun setStatus(roomId: Long, itemId: Long, status: ItemStatus) {
        check(itemDao.updateStatus(roomId, itemId, status) == 1) { "El artículo ya no existe en esta habitación" }
    }

    suspend fun markUnreviewedAsGood(roomId: Long): Int =
        itemDao.updateUnreviewedStatus(roomId, ItemStatus.BUENO)

    suspend fun cleanupUnusedPhotos() = withContext(Dispatchers.IO) {
        val referenced = itemDao.getAllPhotoPaths()
        photos.cleanupUnreferenced(referenced)
    }

    suspend fun savePhoto(roomId: Long, itemId: Long, capture: File) = withContext(Dispatchers.IO) {
        val newPhoto = photos.saveCapture(capture)
        // Una cancelación no puede dejar el archivo nuevo sin referencia a mitad del cambio.
        withContext(NonCancellable) {
            val previous = try {
                database.withTransaction {
                    val item = checkNotNull(itemDao.getById(itemId)) { "El artículo ya no existe" }
                    check(item.roomId == roomId) { "El artículo no pertenece a esta habitación" }
                    check(itemDao.updatePhotoPath(roomId, itemId, newPhoto.path) == 1)
                    item.photoPath
                }
            } catch (error: Exception) {
                photos.delete(newPhoto.path)
                throw error
            }
            photos.delete(previous)
        }
    }

    suspend fun removePhoto(roomId: Long, itemId: Long) = withContext(Dispatchers.IO + NonCancellable) {
        val previous = database.withTransaction {
            val item = checkNotNull(itemDao.getById(itemId)) { "El artículo ya no existe" }
            check(item.roomId == roomId) { "El artículo no pertenece a esta habitación" }
            check(itemDao.updatePhotoPath(roomId, itemId, null) == 1)
            item.photoPath
        }
        photos.delete(previous)
    }

    suspend fun delete(roomId: Long, itemId: Long) = withContext(Dispatchers.IO + NonCancellable) {
        val path = database.withTransaction {
            val item = itemDao.getById(itemId) ?: return@withTransaction null
            check(item.roomId == roomId) { "El artículo no pertenece a esta habitación" }
            check(itemDao.delete(item) == 1)
            item.photoPath
        }
        photos.delete(path)
    }
}
