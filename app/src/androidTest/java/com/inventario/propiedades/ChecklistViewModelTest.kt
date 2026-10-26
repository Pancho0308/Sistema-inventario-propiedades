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
        val model = createViewModel { ChecklistViewModel(ItemRepository(database, photoStorage), roomId) }
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
        val model = createViewModel { ChecklistViewModel(ItemRepository(database, photoStorage), roomId) }
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
        val model = createViewModel { ChecklistViewModel(ItemRepository(database, photoStorage, dao), roomId) }
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
        val model = createViewModel { ChecklistViewModel(ItemRepository(database, photoStorage, dao), roomId) }
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
        val model = createViewModel { ChecklistViewModel(ItemRepository(database, photoStorage, dao), roomId) }
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
        val model = createViewModel { ChecklistViewModel(ItemRepository(database, photoStorage, dao), roomId) }
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

    fun testBulkUsesOneWriteAndChecksUnreviewedAtSaveTime() = runBlocking {
        val roomId = createRoom(createProperty())
        val actual = database.itemDao()
        val first = actual.insert(ItemEntity(roomId = roomId, name = "Mesa"))
        val second = actual.insert(ItemEntity(roomId = roomId, name = "Silla"))
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val dao = object : ItemDao by actual {
            override suspend fun updateUnreviewedStatus(roomId: Long, status: ItemStatus): Int {
                calls++
                assertEquals(ItemStatus.BUENO, status)
                gate.await()
                return actual.updateUnreviewedStatus(roomId, status)
            }
        }
        val model = createViewModel { ChecklistViewModel(ItemRepository(database, photoStorage, dao), roomId) }
        model.uiState.await { !it.isLoading }
        onMain {
            model.markAllAsGood()
            model.markAllAsGood()
            assertTrue(model.isMarkingAll)
            assertEquals(1, calls)
        }
        // Simula otra escritura mientras la operación está pendiente: no debe sobrescribirla.
        actual.updateStatus(roomId, first, ItemStatus.DAÑADO)
        gate.complete(Unit)
        model.uiState.await { it.items.any { item -> item.id == second && item.status == ItemStatus.BUENO } }
        awaitMain { !model.isMarkingAll }
        assertEquals(ItemStatus.DAÑADO, actual.getById(first)!!.status)
        onMain {
            assertEquals(1, calls)
            assertNull(model.saveError)
        }
    }

    fun testBulkFailurePreservesStatusesAndAllowsRetry() = runBlocking {
        val roomId = createRoom(createProperty())
        val actual = database.itemDao()
        val id = actual.insert(ItemEntity(roomId = roomId, name = "Mesa"))
        var failWrite = true
        val dao = object : ItemDao by actual {
            override suspend fun updateUnreviewedStatus(roomId: Long, status: ItemStatus): Int {
                if (failWrite) error("Error de prueba")
                return actual.updateUnreviewedStatus(roomId, status)
            }
        }
        val model = createViewModel { ChecklistViewModel(ItemRepository(database, photoStorage, dao), roomId) }
        onMain { model.markAllAsGood() }
        awaitMain { model.saveError != null && !model.isMarkingAll }
        assertNull(actual.getById(id)!!.status)
        onMain {
            failWrite = false
            model.markAllAsGood()
        }
        model.uiState.await { it.items.singleOrNull()?.status == ItemStatus.BUENO }
        awaitMain { !model.isMarkingAll }
        onMain { assertNull(model.saveError) }
    }

    fun testBulkAndIndividualWritesKeepTapOrder() = runBlocking {
        val roomId = createRoom(createProperty())
        val actual = database.itemDao()
        val first = actual.insert(ItemEntity(roomId = roomId, name = "Mesa"))
        val second = actual.insert(ItemEntity(roomId = roomId, name = "Silla"))
        val gate = CompletableDeferred<Unit>()
        val writes = mutableListOf<String>()
        val dao = object : ItemDao by actual {
            override suspend fun updateStatus(roomId: Long, itemId: Long, status: ItemStatus): Int {
                writes.add(status.name)
                if (writes.size == 1) gate.await()
                return actual.updateStatus(roomId, itemId, status)
            }
            override suspend fun updateUnreviewedStatus(roomId: Long, status: ItemStatus): Int {
                writes.add("all")
                return actual.updateUnreviewedStatus(roomId, status)
            }
        }
        val model = createViewModel { ChecklistViewModel(ItemRepository(database, photoStorage, dao), roomId) }
        onMain {
            model.selectStatus(first, ItemStatus.DAÑADO)
            model.markAllAsGood()
            model.selectStatus(first, ItemStatus.NUEVO)
            assertEquals(listOf("DAÑADO"), writes)
        }
        gate.complete(Unit)
        model.uiState.await { it.items.any { item -> item.id == first && item.status == ItemStatus.NUEVO } }
        awaitMain { !model.isMarkingAll }
        assertEquals(ItemStatus.BUENO, actual.getById(second)!!.status)
        onMain { assertEquals(listOf("DAÑADO", "all", "NUEVO"), writes) }
    }

    fun testRemovePhotoClearsPathAndKeepsItemStatus() = runBlocking {
        val roomId = createRoom(createProperty())
        val dao = database.itemDao()
        val photo = photoStorage.saveCapture(createCapture())
        val item = ItemEntity(roomId = roomId, name = "Mesa", status = ItemStatus.DAÑADO, note = "Nota", photoPath = photo.path)
        val id = dao.insert(item)
        val model = createViewModel { ChecklistViewModel(ItemRepository(database, photoStorage), roomId) }
        model.uiState.await { !it.isLoading }
        onMain { model.removePhoto(id) }
        awaitMain { !model.isRemovingPhoto }
        assertEquals(item.copy(id = id, photoPath = null), dao.getById(id))
        assertFalse(photo.exists())
        onMain { assertNull(model.saveError) }
    }

    fun testRemovePhotoFailureKeepsFileAndAllowsRetry() = runBlocking {
        val roomId = createRoom(createProperty())
        val actual = database.itemDao()
        val photo = photoStorage.saveCapture(createCapture())
        val id = actual.insert(ItemEntity(roomId = roomId, name = "Mesa", photoPath = photo.path))
        var failWrite = true
        val dao = object : ItemDao by actual {
            override suspend fun updatePhotoPath(roomId: Long, itemId: Long, path: String?): Int {
                if (failWrite) error("Error de prueba")
                return actual.updatePhotoPath(roomId, itemId, path)
            }
        }
        val model = createViewModel { ChecklistViewModel(ItemRepository(database, photoStorage, dao), roomId) }
        onMain { model.removePhoto(id) }
        awaitMain { model.saveError != null && !model.isRemovingPhoto }
        assertTrue(photo.exists())
        assertEquals(photo.path, actual.getById(id)!!.photoPath)
        onMain { failWrite = false; model.removePhoto(id) }
        awaitMain { !model.isRemovingPhoto }
        assertFalse(photo.exists())
        assertNull(actual.getById(id)!!.photoPath)
        onMain { assertNull(model.saveError) }
    }

    fun testReadFailureIsVisible() = runBlocking {
        val dao = object : ItemDao by database.itemDao() {
            override fun observeByRoom(roomId: Long): Flow<List<ItemEntity>> = flow { error("Error de lectura de prueba") }
        }
        val model = createViewModel { ChecklistViewModel(ItemRepository(database, photoStorage, dao), 1) }
        val state = model.uiState.await { !it.isLoading }
        assertNotNull(state.error)
        assertTrue(state.items.isEmpty())
    }
}
