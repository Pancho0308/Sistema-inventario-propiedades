package com.inventario.propiedades.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "template_rooms",
    foreignKeys = [ForeignKey(
        entity = TemplateEntity::class,
        parentColumns = ["id"],
        childColumns = ["templateId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("templateId")],
)
data class TemplateRoomEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val name: String,
)
