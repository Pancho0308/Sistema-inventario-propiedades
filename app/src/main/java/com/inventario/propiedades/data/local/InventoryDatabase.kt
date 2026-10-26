package com.inventario.propiedades.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PropertyEntity::class, RoomEntity::class, ItemEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class InventoryDatabase : RoomDatabase() {
    abstract fun propertyDao(): PropertyDao
    abstract fun roomDao(): RoomDao
    abstract fun itemDao(): ItemDao

    companion object {
        @Volatile
        private var instance: InventoryDatabase? = null

        internal val seedCallback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                SeedData.insert(db)
            }
        }

        fun getInstance(context: Context): InventoryDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    InventoryDatabase::class.java,
                    "inventory.db",
                ).addMigrations(MIGRATION_1_2).addCallback(seedCallback).build().also { instance = it }
            }
    }
}
