package com.inventario.propiedades

import com.inventario.propiedades.data.RoomRepository
import com.inventario.propiedades.data.local.ItemEntity
import com.inventario.propiedades.data.local.ItemStatus
import com.inventario.propiedades.data.local.RoomDao
import com.inventario.propiedades.data.local.RoomEntity
import com.inventario.propiedades.data.local.RoomWithProgress
import com.inventario.propiedades.ui.rooms.RoomsViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking

class RoomsViewModelTest : DatabaseTestCase() {
    fun testObservesOnlySelectedPropertyAndRefreshesProgress() = runBlocking {
        val propertyId = createProperty()
        val roomId = createRoom(propertyId)
        createRoom(createProperty(), "Otra propiedad")
        val itemId = database.itemDao().insert(ItemEntity(roomId = roomId, name = "Mesa"))
        val model = createViewModel { RoomsViewModel(RoomRepository(database.roomDao()), propertyId) }
        val initial = model.uiState.await { !it.isLoading }
        assertEquals(listOf(roomId), initial.rooms.map { it.room.id })
        assertEquals(0, initial.rooms.single().reviewedItems)
        database.itemDao().updateStatus(roomId, itemId, ItemStatus.BUENO)
        val updated = model.uiState.await { it.rooms.singleOrNull()?.reviewedItems == 1 }
        assertEquals(1, updated.rooms.single().totalItems)
    }

    fun testRejectsBlankNameWithoutSaving() = runBlocking {
        val propertyId = createProperty()
        val model = createViewModel { RoomsViewModel(RoomRepository(database.roomDao()), propertyId) }
        onMain {
            model.openCreateDialog()
            model.updateName("   ")
            model.createRoom()
            assertNotNull(model.form.error)
            assertFalse(model.form.isSaving)
            assertTrue(model.form.isOpen)
            model.dismissCreateDialog()
            assertFalse(model.form.isOpen)
        }
        assertTrue(database.roomDao().getByProperty(propertyId).isEmpty())
    }

    fun testCreatesTrimmedRoomAndPreventsDuplicateSubmissions() = runBlocking {
        val propertyId = createProperty()
        val actual = database.roomDao()
        val gate = CompletableDeferred<Unit>()
        val dao = object : RoomDao by actual {
            override suspend fun insert(room: RoomEntity): Long {
                gate.await()
                return actual.insert(room)
            }
        }
        val model = createViewModel { RoomsViewModel(RoomRepository(dao), propertyId) }
        onMain {
            model.openCreateDialog()
            model.updateName("  Baño  ")
            model.createRoom()
            model.createRoom()
            model.dismissCreateDialog()
            model.openCreateDialog()
            assertTrue(model.form.isSaving)
            assertEquals("  Baño  ", model.form.name)
        }
        gate.complete(Unit)
        val state = model.uiState.await { it.rooms.size == 1 }
        awaitMain { !model.form.isSaving && !model.form.isOpen }
        assertEquals("Baño", state.rooms.single().room.name)
        assertEquals(0, state.rooms.single().totalItems)
        assertEquals(1, actual.getByProperty(propertyId).size)
    }

    fun testSaveFailurePreservesNameAndAllowsRetry() = runBlocking {
        val propertyId = createProperty()
        val actual = database.roomDao()
        var failWrite = true
        val dao = object : RoomDao by actual {
            override suspend fun insert(room: RoomEntity): Long {
                if (failWrite) error("Error de prueba")
                return actual.insert(room)
            }
        }
        val model = createViewModel { RoomsViewModel(RoomRepository(dao), propertyId) }
        onMain {
            model.openCreateDialog()
            model.updateName("Living")
            model.createRoom()
        }
        awaitMain { model.form.error != null && !model.form.isSaving }
        onMain {
            assertEquals("Living", model.form.name)
            assertTrue(model.form.isOpen)
            failWrite = false
            model.createRoom()
        }
        model.uiState.await { it.rooms.size == 1 }
        awaitMain { !model.form.isOpen }
        assertEquals("Living", actual.getByProperty(propertyId).single().name)
    }

    fun testReadFailureIsVisible() = runBlocking {
        val actual = database.roomDao()
        val dao = object : RoomDao by actual {
            override fun observeWithProgress(propertyId: Long): Flow<List<RoomWithProgress>> = flow {
                error("Error de lectura de prueba")
            }
        }
        val model = createViewModel { RoomsViewModel(RoomRepository(dao), 1) }
        val state = model.uiState.await { !it.isLoading }
        assertNotNull(state.error)
        assertTrue(state.rooms.isEmpty())
    }
}
