package com.inventario.propiedades.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "template_items",
    foreignKeys = [ForeignKey(
        entity = TemplateRoomEntity::class,
        parentColumns = ["id"],
        childColumns = ["templateRoomId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("templateRoomId")],
)
data class TemplateItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateRoomId: Long,
    val name: String,
)
