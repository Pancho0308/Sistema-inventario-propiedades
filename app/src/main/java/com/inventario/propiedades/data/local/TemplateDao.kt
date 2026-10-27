package com.inventario.propiedades.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates ORDER BY id")
    fun observeAll(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates ORDER BY id")
    suspend fun getAll(): List<TemplateEntity>

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun getById(id: Long): TemplateEntity?

    @Query("SELECT * FROM template_rooms WHERE templateId = :templateId ORDER BY id")
    suspend fun getRooms(templateId: Long): List<TemplateRoomEntity>

    @Query("SELECT * FROM template_items WHERE templateRoomId = :roomId ORDER BY id")
    suspend fun getItems(roomId: Long): List<TemplateItemEntity>

    @Insert
    suspend fun insertTemplate(template: TemplateEntity): Long

    @Insert
    suspend fun insertRoom(room: TemplateRoomEntity): Long

    @Insert
    suspend fun insertItem(item: TemplateItemEntity): Long

    @Query("DELETE FROM templates WHERE id = :id AND isBuiltIn = 0")
    suspend fun deleteCustom(id: Long): Int
}
