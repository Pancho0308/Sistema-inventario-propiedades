@file:Suppress("DEPRECATION")

package com.inventario.propiedades

import android.graphics.Bitmap
import android.test.AndroidTestCase
import androidx.room.Room
import com.inventario.propiedades.data.local.InventoryDatabase
import com.inventario.propiedades.data.local.ItemEntity
import com.inventario.propiedades.data.local.ItemStatus
import com.inventario.propiedades.data.local.MIGRATION_1_2
import com.inventario.propiedades.data.local.MIGRATION_2_3
import com.inventario.propiedades.data.local.PropertyEntity
import com.inventario.propiedades.data.local.PropertyStatus
import java.io.File
import kotlinx.coroutines.runBlocking

class MigrationTest : AndroidTestCase() {
    private val databaseName = "migration-1-2-test.db"
    private var migrated: InventoryDatabase? = null
    private var photoFixture: File? = null

    override fun setUp() {
        super.setUp()
        context.deleteDatabase(databaseName)
    }

    override fun tearDown() {
        try {
            migrated?.close()
            context.deleteDatabase(databaseName)
            photoFixture?.delete()
        } finally {
            super.tearDown()
        }
    }

    private fun createLegacyDatabase(emptyItems: Boolean = false, version: Int = 1) {
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
                roomId INTEGER NOT NULL, name TEXT NOT NULL, status TEXT${if (version == 1) " NOT NULL" else ""}, note TEXT NOT NULL, photoPath TEXT,
                FOREIGN KEY(roomId) REFERENCES rooms(id) ON UPDATE NO ACTION ON DELETE CASCADE)
            """.trimIndent())
            db.execSQL("CREATE INDEX index_items_roomId ON items(roomId)")
            db.execSQL("INSERT INTO properties VALUES (7, 'Existente', 'Calle 7', 'PENDIENTE')")
            db.execSQL("INSERT INTO rooms VALUES (11, 7, 'Cocina')")
            db.execSQL("INSERT INTO items VALUES (17, 11, 'Puerta', 'DAÑADO', 'Bisagra rota', '/files/puerta.jpg')")
            db.execSQL("INSERT INTO items VALUES (18, 11, 'Lámpara', 'FALTANTE', '', NULL)")
            db.execSQL("INSERT INTO items VALUES (100, 11, 'Borrado', 'BUENO', '', NULL)")
            db.execSQL(if (emptyItems) "DELETE FROM items" else "DELETE FROM items WHERE id = 100")
            db.version = version
        }
    }

    private fun openMigrated(): InventoryDatabase = Room.databaseBuilder(
        context, InventoryDatabase::class.java, databaseName,
    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).addCallback(InventoryDatabase.seedCallback).build().also { migrated = it }

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
        assertEquals(3, db.openHelper.readableDatabase.version)
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

    private suspend fun assertThreeBaseTemplates(db: InventoryDatabase) {
        val templates = db.templateDao().getAll()
        assertEquals(3, templates.size)
        assertTrue(templates.all { it.isBuiltIn })
        assertEquals(listOf("Estudio", "1D1B", "2D1B"), templates.map { it.name })
        assertEquals(3, templates.map { it.name }.toSet().size)
        val expectedRooms = listOf(
            listOf("Ambiente principal", "Cocina", "Baño"),
            listOf("Living comedor", "Cocina", "Dormitorio", "Baño"),
            listOf("Living comedor", "Cocina", "Dormitorio 1", "Dormitorio 2", "Baño"),
        )
        for ((index, template) in templates.withIndex()) {
            val rooms = db.templateDao().getRooms(template.id)
            assertEquals(expectedRooms[index], rooms.map { it.name })
            for (room in rooms) assertTrue(db.templateDao().getItems(room.id).isNotEmpty())
        }
    }

    fun testNewInstallSeedsExactlyThreeBasesWithoutDuplicatesOnReopen() = runBlocking {
        var db = openMigrated()
        assertThreeBaseTemplates(db)
        val original = db.templateDao().getAll()
        db.close()
        db = openMigrated()
        assertThreeBaseTemplates(db)
        assertEquals(original, db.templateDao().getAll())
    }

    fun testMigrationTwoToThreeSeedsExactlyThreeBasesWithoutDuplicatesOnReopen() = runBlocking {
        createLegacyDatabase(version = 2)
        var db = openMigrated()
        assertThreeBaseTemplates(db)
        val original = db.templateDao().getAll()
        assertEquals("Bisagra rota", db.itemDao().getById(17)!!.note)
        assertEquals("/files/puerta.jpg", db.itemDao().getById(17)!!.photoPath)
        db.close()
        db = openMigrated()
        assertThreeBaseTemplates(db)
        assertEquals(original, db.templateDao().getAll())
        db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
    }

    fun testMigrationOneToTwoToThreePreservesStatusesNotesAndRealPhotoPath() = runBlocking {
        createLegacyDatabase()
        val photo = File.createTempFile("migration-photo-", ".jpg", context.cacheDir)
        photoFixture = photo
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        try { photo.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it)) } }
        finally { bitmap.recycle() }
        val originalBytes = photo.readBytes()
        val expected = listOf(
            ItemEntity(17, 11, "Puerta", ItemStatus.DAÑADO, "Bisagra rota", photo.path),
            ItemEntity(18, 11, "Lámpara", ItemStatus.FALTANTE, "", null),
            ItemEntity(19, 11, "Ventana", ItemStatus.NUEVO, "Recién instalada", "/files/ventana.jpg"),
            ItemEntity(20, 11, "Grifería", ItemStatus.BUENO, "Sin filtraciones", "/files/griferia.jpg"),
            ItemEntity(21, 11, "Piso", ItemStatus.DESGASTADO, "Rayón junto a la puerta", null),
        )
        context.openOrCreateDatabase(databaseName, 0, null).use { legacy ->
            legacy.execSQL("UPDATE properties SET status = 'EN_CURSO' WHERE id = 7")
            legacy.execSQL("UPDATE items SET photoPath = ? WHERE id = 17", arrayOf(photo.path))
            for (item in expected.drop(2)) {
                legacy.execSQL("INSERT INTO items (id, roomId, name, status, note, photoPath) VALUES (?, ?, ?, ?, ?, ?)",
                    arrayOf(item.id, item.roomId, item.name, item.status!!.name, item.note, item.photoPath))
            }
            assertEquals(1, legacy.version)
        }
        val db = openMigrated()
        assertEquals(3, db.openHelper.readableDatabase.version)
        assertEquals(listOf(PropertyEntity(7, "Existente", "Calle 7", PropertyStatus.EN_CURSO)), db.propertyDao().getAll())
        assertEquals("Cocina", db.roomDao().getById(11)!!.name)
        assertEquals(expected, db.itemDao().getByRoom(11))
        assertTrue(originalBytes.contentEquals(photo.readBytes()))
        assertEquals(101L, db.itemDao().insert(ItemEntity(roomId = 11, name = "Después de migrar")))
        assertThreeBaseTemplates(db)
        db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
    }
}
