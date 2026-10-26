package com.inventario.propiedades

import android.database.sqlite.SQLiteConstraintException
import com.inventario.propiedades.data.local.ItemEntity
import com.inventario.propiedades.data.local.ItemStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class ItemDaoTest : DatabaseTestCase() {
    fun testNewItemsStartUnreviewedAndFilterByRoom() = runBlocking {
        val propertyId = createProperty()
        val roomId = createRoom(propertyId)
        val otherId = createRoom(propertyId, "Dormitorio")
        val id = database.itemDao().insert(ItemEntity(roomId = roomId, name = "Mesa"))
        database.itemDao().insert(ItemEntity(roomId = otherId, name = "Cama"))
        val items = database.itemDao().observeByRoom(roomId).first()
        assertEquals(listOf(id), items.map { it.id })
        assertNull(items.single().status)
    }

    fun testStatusUpdatesPreserveFieldsAndRejectWrongRoom() = runBlocking {
        val propertyId = createProperty()
        val roomId = createRoom(propertyId)
        val otherId = createRoom(propertyId, "Otra")
        val original = ItemEntity(roomId = roomId, name = "Mesa", note = "Nota", photoPath = "/files/mesa.jpg")
        val id = database.itemDao().insert(original)
        for (status in ItemStatus.entries) {
            assertEquals(1, database.itemDao().updateStatus(roomId, id, status))
            assertEquals(original.copy(id = id, status = status), database.itemDao().getById(id))
        }
        assertEquals(0, database.itemDao().updateStatus(otherId, id, ItemStatus.NUEVO))
        assertEquals(0, database.itemDao().updateStatus(roomId, Long.MAX_VALUE, ItemStatus.NUEVO))
        assertEquals(ItemStatus.FALTANTE, database.itemDao().getById(id)!!.status)
    }

    fun testBulkStatusOnlyUpdatesUnreviewedItemsInRoom() = runBlocking {
        val propertyId = createProperty()
        val roomId = createRoom(propertyId)
        val otherId = createRoom(propertyId, "Otra")
        val dao = database.itemDao()
        val original = ItemEntity(roomId = roomId, name = "Mesa", note = "Nota", photoPath = "/files/mesa.jpg")
        val id = dao.insert(original)
        val reviewed = ItemStatus.entries.map { status ->
            val item = ItemEntity(roomId = roomId, name = status.name, status = status)
            item.copy(id = dao.insert(item))
        }
        val otherItem = dao.insert(ItemEntity(roomId = otherId, name = "Cama"))
        database.openHelper.readableDatabase.query("SELECT status FROM items WHERE id = $id").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
        }
        assertEquals(1, dao.updateUnreviewedStatus(roomId, ItemStatus.BUENO))
        assertEquals(original.copy(id = id, status = ItemStatus.BUENO), dao.getById(id))
        reviewed.forEach { assertEquals(it, dao.getById(it.id)) }
        assertNull(dao.getById(otherItem)!!.status)
        database.openHelper.readableDatabase.query("SELECT status, typeof(status) FROM items WHERE id = $id").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("BUENO", cursor.getString(0))
            assertEquals("text", cursor.getString(1))
        }
        assertEquals(0, dao.updateUnreviewedStatus(roomId, ItemStatus.BUENO))
        assertEquals(0, dao.updateUnreviewedStatus(Long.MAX_VALUE, ItemStatus.BUENO))
    }

    fun testBulkStatusUpdateRollsBackOnFailure() = runBlocking {
        val roomId = createRoom(createProperty())
        val dao = database.itemDao()
        val first = dao.insert(ItemEntity(roomId = roomId, name = "Mesa"))
        val second = dao.insert(ItemEntity(roomId = roomId, name = "Silla"))
        // Fuerza un fallo dentro del UPDATE para comprobar que no quedan guardados parciales.
        database.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER fail_bulk BEFORE UPDATE OF status ON items
            WHEN NEW.id = $second BEGIN SELECT RAISE(ABORT, 'Error de prueba'); END
        """.trimIndent())
        try {
            dao.updateUnreviewedStatus(roomId, ItemStatus.BUENO)
            fail("Debe propagar el error del guardado")
        } catch (_: SQLiteConstraintException) {
            assertNull(dao.getById(first)!!.status)
            assertNull(dao.getById(second)!!.status)
        }
    }

    fun testItemCrudAndForeignKeys() = runBlocking {
        val roomId = createRoom(createProperty())
        try {
            database.itemDao().insert(ItemEntity(roomId = Long.MAX_VALUE, name = "Huérfano"))
            fail("Debe rechazar un artículo sin habitación")
        } catch (_: SQLiteConstraintException) {
            assertTrue(database.itemDao().getByRoom(roomId).isEmpty())
        }
        val id = database.itemDao().insert(ItemEntity(roomId = roomId, name = "Mesa"))
        val updated = database.itemDao().getById(id)!!.copy(name = "Mesa grande", status = ItemStatus.BUENO)
        assertEquals(1, database.itemDao().update(updated))
        assertEquals(updated, database.itemDao().observeByRoom(roomId).await { it.single().status == ItemStatus.BUENO }.single())
        assertEquals(1, database.itemDao().delete(updated))
        assertNull(database.itemDao().getById(id))
    }
}
