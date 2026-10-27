package com.inventario.propiedades.data

import androidx.room.withTransaction
import com.inventario.propiedades.data.local.InventoryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import com.inventario.propiedades.data.local.PropertyDao
import com.inventario.propiedades.data.local.PropertyEntity
import com.inventario.propiedades.data.local.ItemEntity
import com.inventario.propiedades.data.local.RoomEntity

class PropertyRepository(private val database: InventoryDatabase, private val photos: PhotoStorage) {
    private val propertyDao: PropertyDao = database.propertyDao()
    val properties = propertyDao.observeAll()

    suspend fun create(name: String, address: String, templateId: Long? = null): Long {
        require(name.isNotBlank()) { "El nombre es obligatorio" }
        require(address.isNotBlank()) { "La dirección es obligatoria" }
        return database.withTransaction {
            val templates = database.templateDao()
            if (templateId != null) checkNotNull(templates.getById(templateId)) { "La plantilla ya no existe" }
            val propertyId = propertyDao.insert(PropertyEntity(name = name.trim(), address = address.trim()))
            if (templateId != null) {
                for (room in templates.getRooms(templateId)) {
                    val roomId = database.roomDao().insert(RoomEntity(propertyId = propertyId, name = room.name))
                    for (item in templates.getItems(room.id)) {
                        database.itemDao().insert(ItemEntity(roomId = roomId, name = item.name))
                    }
                }
            }
            propertyId
        }
    }

    suspend fun clone(sourceId: Long, name: String, address: String): Long {
        require(name.isNotBlank()) { "El nombre es obligatorio" }
        require(address.isNotBlank()) { "La dirección es obligatoria" }
        return database.withTransaction {
            checkNotNull(propertyDao.getById(sourceId)) { "La propiedad de origen ya no existe" }
            val propertyId = propertyDao.insert(PropertyEntity(name = name.trim(), address = address.trim()))
            // Los DAO leen por id; insertar en esa misma secuencia conserva el orden del inventario.
            for (room in database.roomDao().getByProperty(sourceId)) {
                val roomId = database.roomDao().insert(RoomEntity(propertyId = propertyId, name = room.name))
                for (item in database.itemDao().getByRoom(room.id)) {
                    database.itemDao().insert(ItemEntity(roomId = roomId, name = item.name))
                }
            }
            propertyId
        }
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
