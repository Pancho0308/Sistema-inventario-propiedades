package com.inventario.propiedades

import android.database.sqlite.SQLiteConstraintException
import com.inventario.propiedades.data.PropertyRepository
import com.inventario.propiedades.data.TemplateRepository
import com.inventario.propiedades.data.local.ItemEntity
import com.inventario.propiedades.data.local.ItemStatus
import com.inventario.propiedades.data.local.PropertyEntity
import com.inventario.propiedades.data.local.PropertyStatus
import com.inventario.propiedades.data.local.RoomEntity
import com.inventario.propiedades.data.local.TemplateEntity
import com.inventario.propiedades.data.local.TemplateItemEntity
import com.inventario.propiedades.data.local.TemplateRoomEntity
import java.io.File
import kotlinx.coroutines.runBlocking

class InventoryCopyTest : DatabaseTestCase() {
    private val expected = listOf("Zócalo" to listOf("Ventana", "Armario"), "Acceso" to listOf("Zócalo", "Alarma"))

    private suspend fun sourceProperty(): Long {
        val id = database.propertyDao().insert(PropertyEntity(name = "Original", address = "Calle 1", status = PropertyStatus.LISTO))
        // El orden de inserción y el alfabético difieren del orden por id, para detectar copias mal ordenadas.
        database.roomDao().insert(RoomEntity(30, id, "Acceso"))
        database.roomDao().insert(RoomEntity(10, id, "Zócalo"))
        val photo = photoStorage.saveCapture(createCapture())
        for (item in listOf(
            ItemEntity(90, 10, "Armario", ItemStatus.DAÑADO, "Bisagra rota", "/files/armario.jpg"),
            ItemEntity(20, 10, "Ventana", ItemStatus.NUEVO, "Nueva", photo.path),
            ItemEntity(70, 30, "Alarma", ItemStatus.FALTANTE, "Falta", null),
            ItemEntity(40, 30, "Zócalo", ItemStatus.BUENO, "Sin daños", "/files/zocalo.jpg"),
        )) database.itemDao().insert(item)
        return id
    }

    private suspend fun assertFreshCopy(id: Long, name: String = "Copia", address: String = "Calle 2") {
        assertEquals(PropertyEntity(id, name, address, PropertyStatus.PENDIENTE), database.propertyDao().getById(id))
        val rooms = database.roomDao().getByProperty(id)
        assertEquals(expected.map { it.first }, rooms.map { it.name })
        for ((index, room) in rooms.withIndex()) {
            val items = database.itemDao().getByRoom(room.id)
            assertEquals(expected[index].second, items.map { it.name })
            for (item in items) assertEquals(ItemEntity(id = item.id, roomId = room.id, name = item.name), item)
        }
    }

    private fun rowCount(table: String): Long = database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use {
        assertTrue(it.moveToFirst())
        it.getLong(0)
    }

    private fun rejectItemInserts(table: String) {
        database.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER fail_copy BEFORE INSERT ON $table WHEN NEW.name = 'Armario'
            BEGIN SELECT RAISE(ABORT, 'Error de prueba'); END
        """.trimIndent())
    }

    fun testClonePreservesIdOrderAndResetsStatusesNotesAndPhotos() = runBlocking {
        val source = sourceProperty()
        val before = database.itemDao().getByRoom(10) + database.itemDao().getByRoom(30)
        val copy = PropertyRepository(database, photoStorage).clone(source, "  Copia  ", "  Calle 2  ")
        assertFreshCopy(copy)
        assertTrue(database.roomDao().getByProperty(copy).all { it.id > 30 })
        assertTrue(database.roomDao().getByProperty(copy).flatMap { database.itemDao().getByRoom(it.id) }.all { it.id > 90 })
        assertEquals(before, database.itemDao().getByRoom(10) + database.itemDao().getByRoom(30))
        assertTrue(File(before.first().photoPath!!).exists())
        assertEquals(PropertyStatus.LISTO, database.propertyDao().getById(source)!!.status)
    }

    fun testSaveTemplatePreservesOrderAndIsIndependentFromSource() = runBlocking {
        val source = sourceProperty()
        val templates = TemplateRepository(database)
        val id = templates.saveFromProperty(source, "  Mi plantilla  ")
        assertEquals(TemplateEntity(id, "Mi plantilla", false), database.templateDao().getById(id))
        val rooms = database.templateDao().getRooms(id)
        assertEquals(expected.map { it.first }, rooms.map { it.name })
        for ((index, room) in rooms.withIndex()) {
            assertEquals(expected[index].second, database.templateDao().getItems(room.id).map { it.name })
        }
        PropertyRepository(database, photoStorage).delete(source)
        val copy = PropertyRepository(database, photoStorage).create("Copia", "Calle 2", id)
        assertFreshCopy(copy)
    }

    fun testCreateFromTemplateReadsRoomAndItemIdsInOrder() = runBlocking {
        val dao = database.templateDao()
        val template = dao.insertTemplate(TemplateEntity(name = "Orden"))
        dao.insertRoom(TemplateRoomEntity(30, template, "Acceso"))
        dao.insertRoom(TemplateRoomEntity(10, template, "Zócalo"))
        dao.insertItem(TemplateItemEntity(90, 10, "Armario"))
        dao.insertItem(TemplateItemEntity(20, 10, "Ventana"))
        dao.insertItem(TemplateItemEntity(70, 30, "Alarma"))
        dao.insertItem(TemplateItemEntity(40, 30, "Zócalo"))
        val copy = PropertyRepository(database, photoStorage).create("Copia", "Calle 2", template)
        assertFreshCopy(copy)
    }

    fun testDeletingCustomTemplateDoesNotDeleteCreatedProperty() = runBlocking {
        val source = sourceProperty()
        val templates = TemplateRepository(database)
        val template = templates.saveFromProperty(source, "Propia")
        val copy = PropertyRepository(database, photoStorage).create("Copia", "Calle 2", template)
        templates.delete(template)
        assertNull(database.templateDao().getById(template))
        assertEquals(0L, rowCount("template_rooms"))
        assertEquals(0L, rowCount("template_items"))
        assertFreshCopy(copy)
    }

    fun testBaseTemplateCannotBeDeletedByRepositoryOrDao() = runBlocking {
        val dao = database.templateDao()
        val id = dao.insertTemplate(TemplateEntity(name = "Base", isBuiltIn = true))
        val room = dao.insertRoom(TemplateRoomEntity(templateId = id, name = "Baño"))
        dao.insertItem(TemplateItemEntity(templateRoomId = room, name = "WC"))
        assertEquals(0, dao.deleteCustom(id))
        try {
            TemplateRepository(database).delete(id)
            fail("No debe eliminar una plantilla base")
        } catch (_: IllegalStateException) {
            assertNotNull(dao.getById(id))
            assertEquals("WC", dao.getItems(room).single().name)
        }
    }

    fun testCloneRollsBackWholePropertyWhenAnItemFails() = runBlocking {
        val source = sourceProperty()
        rejectItemInserts("items")
        try {
            PropertyRepository(database, photoStorage).clone(source, "Copia", "Calle 2")
            fail("La copia debe fallar sin guardar partes")
        } catch (_: SQLiteConstraintException) {
            assertEquals(1L, rowCount("properties"))
            assertEquals(2L, rowCount("rooms"))
            assertEquals(4L, rowCount("items"))
        }
    }

    fun testCreateFromTemplateRollsBackWholePropertyWhenAnItemFails() = runBlocking {
        val source = sourceProperty()
        val template = TemplateRepository(database).saveFromProperty(source, "Propia")
        rejectItemInserts("items")
        try {
            PropertyRepository(database, photoStorage).create("Copia", "Calle 2", template)
            fail("Debe revertir propiedad, habitaciones y artículos")
        } catch (_: SQLiteConstraintException) {
            assertEquals(1L, rowCount("properties"))
            assertEquals(2L, rowCount("rooms"))
            assertEquals(4L, rowCount("items"))
            assertEquals(1L, rowCount("templates"))
        }
    }

    fun testSaveTemplateRollsBackAllItsTablesWhenAnItemFails() = runBlocking {
        val source = sourceProperty()
        rejectItemInserts("template_items")
        try {
            TemplateRepository(database).saveFromProperty(source, "Propia")
            fail("Debe revertir la plantilla completa")
        } catch (_: SQLiteConstraintException) {
            assertEquals(0L, rowCount("templates"))
            assertEquals(0L, rowCount("template_rooms"))
            assertEquals(0L, rowCount("template_items"))
            assertEquals(4L, rowCount("items"))
        }
    }

    fun testMissingSourcesAndBlankNamesNeverCreatePartialCopies() = runBlocking {
        val source = sourceProperty()
        val properties = PropertyRepository(database, photoStorage)
        val templates = TemplateRepository(database)
        val operations: List<suspend () -> Unit> = listOf(
            { properties.clone(Long.MAX_VALUE, "Copia", "Calle 2") },
            { properties.create("Copia", "Calle 2", Long.MAX_VALUE) },
            { templates.saveFromProperty(Long.MAX_VALUE, "Propia") },
            { properties.clone(source, "  ", "Calle 2") },
            { properties.clone(source, "Copia", " ") },
            { templates.saveFromProperty(source, " ") },
        )
        for (operation in operations) {
            try { operation(); fail("Debe validar el origen y los campos obligatorios") }
            catch (_: IllegalStateException) { }
            catch (_: IllegalArgumentException) { }
        }
        assertEquals(1L, rowCount("properties"))
        assertEquals(0L, rowCount("templates"))
    }

    fun testEmptyRoomsAndEmptyPropertiesCanBeCopied() = runBlocking {
        val source = createProperty()
        val properties = PropertyRepository(database, photoStorage)
        val emptyCopy = properties.clone(source, "Vacía", "Calle 2")
        assertTrue(database.roomDao().getByProperty(emptyCopy).isEmpty())
        createRoom(source, "Sin artículos")
        val template = TemplateRepository(database).saveFromProperty(source, "Vacía")
        val copy = properties.create("Desde plantilla", "Calle 3", template)
        val room = database.roomDao().getByProperty(copy).single()
        assertEquals("Sin artículos", room.name)
        assertTrue(database.itemDao().getByRoom(room.id).isEmpty())
    }
}
