package com.inventario.propiedades.data

import com.inventario.propiedades.data.local.ItemDao
import com.inventario.propiedades.data.local.ItemEntity
import com.inventario.propiedades.data.local.ItemStatus

class ItemRepository(private val itemDao: ItemDao) {
    fun observeItems(roomId: Long) = itemDao.observeByRoom(roomId)

    suspend fun create(roomId: Long, name: String): Long {
        require(name.isNotBlank()) { "El nombre es obligatorio" }
        return itemDao.insert(ItemEntity(roomId = roomId, name = name.trim()))
    }

    suspend fun setStatus(roomId: Long, itemId: Long, status: ItemStatus) {
        check(itemDao.updateStatus(roomId, itemId, status) == 1) { "El artículo ya no existe en esta habitación" }
    }
}
