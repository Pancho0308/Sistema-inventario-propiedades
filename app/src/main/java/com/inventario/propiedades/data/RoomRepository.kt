package com.inventario.propiedades.data

import com.inventario.propiedades.data.local.RoomDao
import com.inventario.propiedades.data.local.RoomEntity

class RoomRepository(private val roomDao: RoomDao) {
    fun observeRooms(propertyId: Long) = roomDao.observeWithProgress(propertyId)

    suspend fun create(propertyId: Long, name: String): Long {
        require(name.isNotBlank()) { "El nombre es obligatorio" }
        return roomDao.insert(RoomEntity(propertyId = propertyId, name = name.trim()))
    }
}
