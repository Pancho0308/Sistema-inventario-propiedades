package com.inventario.propiedades.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT photoPath FROM items WHERE photoPath IS NOT NULL")
    suspend fun getAllPhotoPaths(): List<String>

    @Query("SELECT * FROM items WHERE roomId = :roomId ORDER BY id")
    fun observeByRoom(roomId: Long): Flow<List<ItemEntity>>

    @Query("UPDATE items SET status = :status WHERE id = :itemId AND roomId = :roomId")
    suspend fun updateStatus(roomId: Long, itemId: Long, status: ItemStatus): Int

    @Query("UPDATE items SET status = :status WHERE roomId = :roomId AND status IS NULL")
    suspend fun updateUnreviewedStatus(roomId: Long, status: ItemStatus): Int

    @Query("UPDATE items SET photoPath = :path WHERE id = :itemId AND roomId = :roomId")
    suspend fun updatePhotoPath(roomId: Long, itemId: Long, path: String?): Int

    @Query("SELECT photoPath FROM items WHERE roomId = :roomId AND photoPath IS NOT NULL")
    suspend fun getPhotoPathsByRoom(roomId: Long): List<String>

    @Query("""
        SELECT items.photoPath FROM items INNER JOIN rooms ON items.roomId = rooms.id
        WHERE rooms.propertyId = :propertyId AND items.photoPath IS NOT NULL
    """)
    suspend fun getPhotoPathsByProperty(propertyId: Long): List<String>

    @Query("SELECT * FROM items WHERE roomId = :roomId ORDER BY id")
    suspend fun getByRoom(roomId: Long): List<ItemEntity>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getById(id: Long): ItemEntity?

    @Insert
    suspend fun insert(item: ItemEntity): Long

    @Update
    suspend fun update(item: ItemEntity): Int

    @Delete
    suspend fun delete(item: ItemEntity): Int
}
