package com.inventario.propiedades

import android.database.sqlite.SQLiteConstraintException
import com.inventario.propiedades.data.local.ItemEntity
import com.inventario.propiedades.data.local.ItemStatus
import com.inventario.propiedades.data.local.RoomEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class RoomDaoTest : DatabaseTestCase() {
    fun testProgressIncludesEmptyRoomsAndFiltersProperty() = runBlocking {
        val propertyId = createProperty()
        val kitchenId = createRoom(propertyId)
        val emptyId = createRoom(propertyId, "Living")
        val otherId = createRoom(createProperty(), "Otra propiedad")
        database.itemDao().insert(ItemEntity(roomId = kitchenId, name = "Sin revisar"))
        database.itemDao().insert(ItemEntity(roomId = kitchenId, name = "Nuevo", status = ItemStatus.NUEVO))
        database.itemDao().insert(ItemEntity(roomId = kitchenId, name = "Ausente", status = ItemStatus.FALTANTE))
        database.itemDao().insert(ItemEntity(roomId = otherId, name = "Otro", status = ItemStatus.BUENO))

        val rooms = database.roomDao().observeWithProgress(propertyId).first()
        assertEquals(listOf(kitchenId, emptyId), rooms.map { it.room.id })
        assertEquals(3, rooms[0].totalItems)
        assertEquals(2, rooms[0].reviewedItems)
        assertEquals(0, rooms[1].totalItems)
        assertEquals(0, rooms[1].reviewedItems)
    }

    fun testProgressFlowReflectsReviewsAndNewItems() = runBlocking {
        val propertyId = createProperty()
        val roomId = createRoom(propertyId)
        val itemId = database.itemDao().insert(ItemEntity(roomId = roomId, name = "Mesa"))
        val progress = database.roomDao().observeWithProgress(propertyId)
        assertEquals(0, progress.first().single().reviewedItems)
        database.itemDao().updateStatus(roomId, itemId, ItemStatus.FALTANTE)
        val reviewed = progress.await { it.single().reviewedItems == 1 }.single()
        assertEquals(1, reviewed.totalItems)
        database.itemDao().insert(ItemEntity(roomId = roomId, name = "Silla"))
        val updated = progress.await { it.single().totalItems == 2 }.single()
        assertEquals(1, updated.reviewedItems)
    }

    fun testRoomCrudForeignKeysAndCascade() = runBlocking {
        val propertyId = createProperty()
        val roomId = createRoom(propertyId)
        val room = database.roomDao().getById(roomId)!!
        assertEquals(1, database.roomDao().update(room.copy(name = "Cocina principal")))
        assertEquals("Cocina principal", database.roomDao().getByProperty(propertyId).single().name)
        try {
            database.roomDao().insert(RoomEntity(propertyId = Long.MAX_VALUE, name = "Huérfana"))
            fail("Debe rechazar una habitación sin propiedad")
        } catch (_: SQLiteConstraintException) {
            assertEquals(1, database.roomDao().getByProperty(propertyId).size)
        }
        val itemId = database.itemDao().insert(ItemEntity(roomId = roomId, name = "Mesa"))
        assertEquals(1, database.roomDao().delete(room))
        assertNull(database.roomDao().getById(roomId))
        assertNull(database.itemDao().getById(itemId))
        assertNotNull(database.propertyDao().getById(propertyId))
    }
}
