package com.inventario.propiedades.data

import androidx.room.withTransaction
import com.inventario.propiedades.data.local.InventoryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import com.inventario.propiedades.data.local.PropertyDao
import com.inventario.propiedades.data.local.PropertyEntity

class PropertyRepository(private val database: InventoryDatabase, private val photos: PhotoStorage) {
    private val propertyDao: PropertyDao = database.propertyDao()
    val properties = propertyDao.observeAll()

    suspend fun create(name: String, address: String): Long {
        require(name.isNotBlank()) { "El nombre es obligatorio" }
        require(address.isNotBlank()) { "La dirección es obligatoria" }
        return propertyDao.insert(PropertyEntity(name = name.trim(), address = address.trim()))
    }

    suspend fun delete(propertyId: Long) = withContext(Dispatchers.IO + NonCancellable) {
        val paths = database.withTransaction {
            val property = propertyDao.getById(propertyId) ?: return@withTransaction emptyList()
            // Recoge las rutas antes de que la cascada elimine habitaciones y artículos.
            val paths = database.itemDao().getPhotoPathsByProperty(propertyId)
            check(propertyDao.delete(property) == 1)
            paths
        }
        photos.deleteAll(paths)
    }
}
