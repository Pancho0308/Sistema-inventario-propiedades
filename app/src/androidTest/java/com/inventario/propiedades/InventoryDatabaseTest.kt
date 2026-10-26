@file:Suppress("DEPRECATION")

package com.inventario.propiedades

import android.database.sqlite.SQLiteConstraintException
import android.test.AndroidTestCase
import androidx.room.Room
import com.inventario.propiedades.data.local.InventoryDatabase
import com.inventario.propiedades.data.local.ItemEntity
import com.inventario.propiedades.data.local.ItemStatus
import com.inventario.propiedades.data.local.PropertyEntity
import com.inventario.propiedades.data.local.RoomEntity
import kotlinx.coroutines.runBlocking

// Usa el runner de Android para no incorporar dependencias de pruebas adicionales.
class InventoryDatabaseTest : AndroidTestCase() {
    private lateinit var database: InventoryDatabase
    private val databaseName = "inventory-test.db"

    override fun setUp() {
        super.setUp()
        context.deleteDatabase(databaseName)
        database = openDatabase()
    }

    override fun tearDown() {
        try {
            database.close()
            context.deleteDatabase(databaseName)
        } finally {
            super.tearDown()
        }
    }

    private fun openDatabase(): InventoryDatabase = Room.databaseBuilder(
        context,
        InventoryDatabase::class.java,
        databaseName,
    ).addCallback(InventoryDatabase.seedCallback).build()

    fun testSeedAndReopen() = runBlocking {
        val property = database.propertyDao().getAll().single()
        val rooms = database.roomDao().getByProperty(property.id)
        val items = rooms.flatMap { database.itemDao().getByRoom(it.id) }
        assertEquals(3, rooms.size)
        assertEquals(5, items.size)
        assertEquals(ItemStatus.entries.toSet(), items.map { it.status }.toSet())
        assertTrue(items.all { it.photoPath == null })

        val updated = items.first().copy(note = "Nota guardada", photoPath = "/local/sofa.jpg")
        assertEquals(1, database.itemDao().update(updated))
        database.close()
        database = openDatabase()

        assertEquals(updated, database.itemDao().getById(updated.id))
        assertEquals(1, database.propertyDao().getAll().size)
        assertEquals(3, database.roomDao().getByProperty(property.id).size)
        assertEquals(5, rooms.sumOf { database.itemDao().getByRoom(it.id).size })
    }

    fun testCrudAndCascade() = runBlocking {
        val propertyId = database.propertyDao().insert(PropertyEntity(name = "Nuevo", address = "Calle 1"))
        val roomId = database.roomDao().insert(RoomEntity(propertyId = propertyId, name = "Baño"))
        val itemId = database.itemDao().insert(ItemEntity(roomId = roomId, name = "Espejo", status = ItemStatus.BUENO))
        val property = database.propertyDao().getById(propertyId)!!
        val room = database.roomDao().getById(roomId)!!
        assertEquals(1, database.propertyDao().update(property.copy(name = "Actualizado")))
        assertEquals("Actualizado", database.propertyDao().getById(propertyId)!!.name)
        assertEquals(1, database.roomDao().update(room.copy(name = "Baño principal")))
        assertEquals("Baño principal", database.roomDao().getById(roomId)!!.name)

        val item = database.itemDao().getById(itemId)!!
        assertEquals(1, database.itemDao().delete(item))
        assertNull(database.itemDao().getById(itemId))
        val secondItemId = database.itemDao().insert(item.copy(id = 0))
        assertEquals(1, database.roomDao().delete(room))
        assertNull(database.itemDao().getById(secondItemId))

        val secondRoomId = database.roomDao().insert(room.copy(id = 0))
        val thirdItemId = database.itemDao().insert(item.copy(id = 0, roomId = secondRoomId))
        assertEquals(1, database.propertyDao().delete(property))
        assertNull(database.propertyDao().getById(propertyId))
        assertNull(database.roomDao().getById(secondRoomId))
        assertNull(database.itemDao().getById(thirdItemId))
    }

    fun testForeignKeysAndDuplicateProtection() = runBlocking {
        val original = database.propertyDao().getAll().single()
        try {
            database.propertyDao().insert(original.copy(name = "Duplicado"))
            fail("Debe rechazar identificadores duplicados")
        } catch (_: SQLiteConstraintException) {
            assertEquals(original, database.propertyDao().getById(original.id))
            assertEquals(3, database.roomDao().getByProperty(original.id).size)
        }
        try {
            database.roomDao().insert(RoomEntity(propertyId = Long.MAX_VALUE, name = "Huérfana"))
            fail("Debe rechazar habitaciones sin propiedad")
        } catch (_: SQLiteConstraintException) {
            assertEquals(3, database.roomDao().getByProperty(original.id).size)
        }
        try {
            database.itemDao().insert(ItemEntity(roomId = Long.MAX_VALUE, name = "Huérfano", status = ItemStatus.NUEVO))
            fail("Debe rechazar artículos sin habitación")
        } catch (_: SQLiteConstraintException) {
            assertNull(database.itemDao().getById(Long.MAX_VALUE))
        }
    }
}
