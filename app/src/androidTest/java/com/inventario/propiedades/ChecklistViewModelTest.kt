package com.inventario.propiedades

import com.inventario.propiedades.data.ItemRepository
import com.inventario.propiedades.data.local.ItemDao
import com.inventario.propiedades.data.local.ItemEntity
import com.inventario.propiedades.data.local.ItemStatus
import com.inventario.propiedades.ui.checklist.ChecklistViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking

class ChecklistViewModelTest : DatabaseTestCase() {
    fun testCreatesUnreviewedItemAndFiltersRoom() = runBlocking {
        val propertyId = createProperty()
        val roomId = createRoom(propertyId)
        val otherId = createRoom(propertyId, "Dormitorio")
        database.itemDao().insert(ItemEntity(roomId = otherId, name = "Cama"))
        val model = createViewModel { ChecklistViewModel(ItemRepository(database.itemDao()), roomId) }
        assertTrue(model.uiState.await { !it.isLoading }.items.isEmpty())
        onMain {
            model.updateNewItemName("  Mesa  ")
            model.addItem()
        }
        val item = model.uiState.await { it.items.size == 1 }.items.single()
        awaitMain { !model.isAdding }
        assertEquals("Mesa", item.name)
        assertNull(item.status)
        onMain { assertEquals("", model.newItemName) }
    }

    fun testRejectsBlankName() = runBlocking {
        val roomId = createRoom(createProperty())
        val model = createViewModel { ChecklistViewModel(ItemRepository(database.itemDao()), roomId) }
        onMain {
            model.updateNewItemName("   ")
            model.addItem()
            assertNotNull(model.addError)
            assertFalse(model.isAdding)
        }
        assertTrue(database.itemDao().getByRoom(roomId).isEmpty())
    }

    fun testPendingAddDoesNotDuplicateOrEraseNewInput() = runBlocking {
        val roomId = createRoom(createProperty())
        val actual = database.itemDao()
        val gate = CompletableDeferred<Unit>()
        val dao = object : ItemDao by actual {
            override suspend fun insert(item: ItemEntity): Long {
                gate.await()
                return actual.insert(item)
            }
        }
        val model = createViewModel { ChecklistViewModel(ItemRepository(dao), roomId) }
        onMain {
            model.updateNewItemName("Mesa")
            model.addItem()
            model.addItem()
            model.updateNewItemName("Silla")
            assertTrue(model.isAdding)
        }
        gate.complete(Unit)
        model.uiState.await { it.items.size == 1 }
        awaitMain { !model.isAdding }
        assertEquals("Mesa", actual.getByRoom(roomId).single().name)
        onMain { assertEquals("Silla", model.newItemName) }
    }

    fun testAddFailurePreservesNameAndAllowsRetry() = runBlocking {
        val roomId = createRoom(createProperty())
        val actual = database.itemDao()
        var failWrite = true
        val dao = object : ItemDao by actual {
            override suspend fun insert(item: ItemEntity): Long {
                if (failWrite) error("Error de prueba")
                return actual.insert(item)
            }
        }
        val model = createViewModel { ChecklistViewModel(ItemRepository(dao), roomId) }
        onMain {
            model.updateNewItemName("Silla")
            model.addItem()
        }
        awaitMain { model.addError != null && !model.isAdding }
        onMain {
            assertEquals("Silla", model.newItemName)
            failWrite = false
            model.addItem()
        }
        model.uiState.await { it.items.size == 1 }
        awaitMain { !model.isAdding }
        onMain { assertNull(model.addError) }
    }

    fun testRapidStatusTapsAreSavedInOrder() = runBlocking {
        val roomId = createRoom(createProperty())
        val actual = database.itemDao()
        val id = actual.insert(ItemEntity(roomId = roomId, name = "Mesa", note = "Nota", photoPath = "/files/mesa.jpg"))
        val gate = CompletableDeferred<Unit>()
        val writes = mutableListOf<ItemStatus>()
        val dao = object : ItemDao by actual {
            override suspend fun updateStatus(roomId: Long, itemId: Long, status: ItemStatus): Int {
                writes.add(status)
                if (writes.size == 1) gate.await()
                return actual.updateStatus(roomId, itemId, status)
            }
        }
        val model = createViewModel { ChecklistViewModel(ItemRepository(dao), roomId) }
        onMain {
            ItemStatus.entries.forEach { model.selectStatus(id, it) }
            assertEquals(listOf(ItemStatus.NUEVO), writes)
        }
        gate.complete(Unit)
        model.uiState.await { it.items.singleOrNull()?.status == ItemStatus.FALTANTE }
        onMain {
            assertEquals(ItemStatus.entries.toList(), writes)
            assertNull(model.saveError)
        }
        val saved = actual.getById(id)!!
        assertEquals("Nota", saved.note)
        assertEquals("/files/mesa.jpg", saved.photoPath)
    }

    fun testStatusFailureDoesNotPretendSavedAndAllowsRetry() = runBlocking {
        val roomId = createRoom(createProperty())
        val actual = database.itemDao()
        val id = actual.insert(ItemEntity(roomId = roomId, name = "Mesa"))
        var failWrite = true
        val dao = object : ItemDao by actual {
            override suspend fun updateStatus(roomId: Long, itemId: Long, status: ItemStatus): Int {
                if (failWrite) error("Error de prueba")
                return actual.updateStatus(roomId, itemId, status)
            }
        }
        val model = createViewModel { ChecklistViewModel(ItemRepository(dao), roomId) }
        onMain { model.selectStatus(id, ItemStatus.DAÑADO) }
        awaitMain { model.saveError != null }
        assertNull(actual.getById(id)!!.status)
        onMain {
            failWrite = false
            model.selectStatus(id, ItemStatus.BUENO)
        }
        model.uiState.await { it.items.singleOrNull()?.status == ItemStatus.BUENO }
        onMain { assertNull(model.saveError) }
    }

    fun testReadFailureIsVisible() = runBlocking {
        val dao = object : ItemDao by database.itemDao() {
            override fun observeByRoom(roomId: Long): Flow<List<ItemEntity>> = flow { error("Error de lectura de prueba") }
        }
        val model = createViewModel { ChecklistViewModel(ItemRepository(dao), 1) }
        val state = model.uiState.await { !it.isLoading }
        assertNotNull(state.error)
        assertTrue(state.items.isEmpty())
    }
}
