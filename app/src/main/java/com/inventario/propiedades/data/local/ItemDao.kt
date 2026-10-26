package com.inventario.propiedades.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE roomId = :roomId ORDER BY id")
    fun observeByRoom(roomId: Long): Flow<List<ItemEntity>>

    @Query("UPDATE items SET status = :status WHERE id = :itemId AND roomId = :roomId")
    suspend fun updateStatus(roomId: Long, itemId: Long, status: ItemStatus): Int

    @Query("UPDATE items SET status = :status WHERE roomId = :roomId AND status IS NULL")
    suspend fun updateUnreviewedStatus(roomId: Long, status: ItemStatus): Int

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
