package com.inventario.propiedades.data

import androidx.room.withTransaction
import com.inventario.propiedades.data.local.InventoryDatabase
import com.inventario.propiedades.data.local.TemplateEntity
import com.inventario.propiedades.data.local.TemplateItemEntity
import com.inventario.propiedades.data.local.TemplateRoomEntity

class TemplateRepository(private val database: InventoryDatabase) {
    private val templateDao = database.templateDao()
    val templates = templateDao.observeAll()

    suspend fun saveFromProperty(propertyId: Long, name: String): Long {
        require(name.isNotBlank()) { "El nombre de la plantilla es obligatorio" }
        return database.withTransaction {
            checkNotNull(database.propertyDao().getById(propertyId)) { "La propiedad ya no existe" }
            val templateId = templateDao.insertTemplate(TemplateEntity(name = name.trim()))
            for (room in database.roomDao().getByProperty(propertyId)) {
                val roomId = templateDao.insertRoom(TemplateRoomEntity(templateId = templateId, name = room.name))
                for (item in database.itemDao().getByRoom(room.id)) {
                    templateDao.insertItem(TemplateItemEntity(templateRoomId = roomId, name = item.name))
                }
            }
            templateId
        }
    }

    suspend fun delete(templateId: Long) {
        check(templateDao.deleteCustom(templateId) == 1) { "La plantilla no existe o es una plantilla base" }
    }
}
