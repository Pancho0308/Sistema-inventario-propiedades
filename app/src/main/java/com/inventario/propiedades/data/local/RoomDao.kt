package com.inventario.propiedades.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomDao {
    @Query("""
        SELECT rooms.*, COUNT(items.id) AS totalItems, COUNT(items.status) AS reviewedItems
        FROM rooms LEFT JOIN items ON items.roomId = rooms.id
        WHERE rooms.propertyId = :propertyId
        GROUP BY rooms.id ORDER BY rooms.id
    """)
    fun observeWithProgress(propertyId: Long): Flow<List<RoomWithProgress>>

    @Query("SELECT * FROM rooms WHERE propertyId = :propertyId ORDER BY id")
    suspend fun getByProperty(propertyId: Long): List<RoomEntity>

    @Query("SELECT * FROM rooms WHERE id = :id")
    suspend fun getById(id: Long): RoomEntity?

    @Insert
    suspend fun insert(room: RoomEntity): Long

    @Update
    suspend fun update(room: RoomEntity): Int

    // El borrado elimina también los artículos de esta habitación.
    @Delete
    suspend fun delete(room: RoomEntity): Int
}
