package com.inventario.propiedades.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "properties")
data class PropertyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val address: String,
    val status: PropertyStatus = PropertyStatus.PENDIENTE,
)
