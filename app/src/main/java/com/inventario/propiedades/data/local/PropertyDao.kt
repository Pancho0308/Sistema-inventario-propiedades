package com.inventario.propiedades.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface PropertyDao {
    @Query("SELECT * FROM properties ORDER BY id")
    suspend fun getAll(): List<PropertyEntity>

    @Query("SELECT * FROM properties WHERE id = :id")
    suspend fun getById(id: Long): PropertyEntity?

    @Insert
    suspend fun insert(property: PropertyEntity): Long

    @Update
    suspend fun update(property: PropertyEntity): Int

    // El borrado elimina también sus habitaciones y artículos mediante claves foráneas.
    @Delete
    suspend fun delete(property: PropertyEntity): Int
}
