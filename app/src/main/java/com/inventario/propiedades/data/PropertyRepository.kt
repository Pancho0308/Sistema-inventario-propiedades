package com.inventario.propiedades.data

import com.inventario.propiedades.data.local.PropertyDao
import com.inventario.propiedades.data.local.PropertyEntity

class PropertyRepository(private val propertyDao: PropertyDao) {
    val properties = propertyDao.observeAll()

    suspend fun create(name: String, address: String): Long {
        require(name.isNotBlank()) { "El nombre es obligatorio" }
        require(address.isNotBlank()) { "La dirección es obligatoria" }
        return propertyDao.insert(PropertyEntity(name = name.trim(), address = address.trim()))
    }
}
