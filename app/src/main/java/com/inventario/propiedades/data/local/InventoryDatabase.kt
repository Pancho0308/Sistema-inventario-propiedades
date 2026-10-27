package com.inventario.propiedades.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PropertyEntity::class, RoomEntity::class, ItemEntity::class,
        TemplateEntity::class, TemplateRoomEntity::class, TemplateItemEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class InventoryDatabase : RoomDatabase() {
    abstract fun propertyDao(): PropertyDao
    abstract fun roomDao(): RoomDao
    abstract fun itemDao(): ItemDao
    abstract fun templateDao(): TemplateDao

    companion object {
        @Volatile
        private var instance: InventoryDatabase? = null

        internal val seedCallback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                SeedData.insert(db)
                TemplateSeedData.insert(db)
            }
        }

        fun getInstance(context: Context): InventoryDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    InventoryDatabase::class.java,
                    "inventory.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).addCallback(seedCallback).build().also { instance = it }
            }
    }
}
