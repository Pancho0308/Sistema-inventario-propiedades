@file:Suppress("DEPRECATION")

package com.inventario.propiedades

import android.test.AndroidTestCase
import androidx.room.Room
import com.inventario.propiedades.data.local.InventoryDatabase
import com.inventario.propiedades.data.local.ItemEntity
import com.inventario.propiedades.data.local.ItemStatus
import com.inventario.propiedades.data.local.MIGRATION_1_2
import kotlinx.coroutines.runBlocking

class MigrationTest : AndroidTestCase() {
    private val databaseName = "migration-1-2-test.db"
    private var migrated: InventoryDatabase? = null

    override fun setUp() {
        super.setUp()
        context.deleteDatabase(databaseName)
    }

    override fun tearDown() {
        try {
            migrated?.close()
            context.deleteDatabase(databaseName)
        } finally {
            super.tearDown()
        }
    }

    private fun createLegacyDatabase(emptyItems: Boolean = false) {
        context.openOrCreateDatabase(databaseName, 0, null).use { db ->
            // Reproduce el esquema exportado de la versión 1, no la entidad actual.
            db.execSQL("""
                CREATE TABLE properties (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL, address TEXT NOT NULL, status TEXT NOT NULL)
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE rooms (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                propertyId INTEGER NOT NULL, name TEXT NOT NULL,
                FOREIGN KEY(propertyId) REFERENCES properties(id) ON UPDATE NO ACTION ON DELETE CASCADE)
            """.trimIndent())
            db.execSQL("CREATE INDEX index_rooms_propertyId ON rooms(propertyId)")
            db.execSQL("""
                CREATE TABLE items (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                roomId INTEGER NOT NULL, name TEXT NOT NULL, status TEXT NOT NULL, note TEXT NOT NULL, photoPath TEXT,
                FOREIGN KEY(roomId) REFERENCES rooms(id) ON UPDATE NO ACTION ON DELETE CASCADE)
            """.trimIndent())
            db.execSQL("CREATE INDEX index_items_roomId ON items(roomId)")
            db.execSQL("INSERT INTO properties VALUES (7, 'Existente', 'Calle 7', 'PENDIENTE')")
            db.execSQL("INSERT INTO rooms VALUES (11, 7, 'Cocina')")
            db.execSQL("INSERT INTO items VALUES (17, 11, 'Puerta', 'DAÑADO', 'Bisagra rota', '/files/puerta.jpg')")
            db.execSQL("INSERT INTO items VALUES (18, 11, 'Lámpara', 'FALTANTE', '', NULL)")
            db.execSQL("INSERT INTO items VALUES (100, 11, 'Borrado', 'BUENO', '', NULL)")
            db.execSQL(if (emptyItems) "DELETE FROM items" else "DELETE FROM items WHERE id = 100")
            db.version = 1
        }
    }

    private fun openMigrated(): InventoryDatabase = Room.databaseBuilder(
        context, InventoryDatabase::class.java, databaseName,
    ).addMigrations(MIGRATION_1_2).addCallback(InventoryDatabase.seedCallback).build().also { migrated = it }

    fun testMigrationPreservesDataAndAllowsNullStatus() = runBlocking {
        createLegacyDatabase()
        val db = openMigrated()
        val items = db.itemDao().getByRoom(11)
        assertEquals(listOf(17L, 18L), items.map { it.id })
        assertEquals(ItemEntity(17, 11, "Puerta", ItemStatus.DAÑADO, "Bisagra rota", "/files/puerta.jpg"), items[0])
        assertEquals(ItemStatus.FALTANTE, items[1].status)
        assertEquals(1, db.propertyDao().getAll().size)
        assertEquals("Existente", db.propertyDao().getById(7)!!.name)
        assertEquals("Cocina", db.roomDao().getById(11)!!.name)
        val newId = db.itemDao().insert(ItemEntity(roomId = 11, name = "Nuevo"))
        assertEquals(101L, newId)
        assertNull(db.itemDao().getById(newId)!!.status)
        assertEquals(2, db.openHelper.readableDatabase.version)
        db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
    }

    fun testMigrationPreservesSequenceWhenItemsAreEmpty() = runBlocking {
        createLegacyDatabase(emptyItems = true)
        val db = openMigrated()
        assertTrue(db.itemDao().getByRoom(11).isEmpty())
        val id = db.itemDao().insert(ItemEntity(roomId = 11, name = "Nuevo"))
        assertEquals(101L, id)
        db.roomDao().delete(db.roomDao().getById(11)!!)
        assertNull(db.itemDao().getById(id))
    }
}
