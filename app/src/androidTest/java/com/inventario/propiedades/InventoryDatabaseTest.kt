@file:Suppress("DEPRECATION")

package com.inventario.propiedades

import android.database.sqlite.SQLiteConstraintException
import android.os.Handler
import android.os.Looper
import android.test.AndroidTestCase
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import com.inventario.propiedades.data.PropertyRepository
import com.inventario.propiedades.data.PhotoStorage
import java.io.File
import com.inventario.propiedades.data.local.InventoryDatabase
import com.inventario.propiedades.data.local.ItemEntity
import com.inventario.propiedades.data.local.ItemStatus
import com.inventario.propiedades.data.local.PropertyEntity
import com.inventario.propiedades.data.local.PropertyStatus
import com.inventario.propiedades.data.local.RoomEntity
import com.inventario.propiedades.ui.navigation.RoomsRoute
import com.inventario.propiedades.ui.properties.PropertyViewModel
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PrimitiveKind

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

    fun testRepositoryFlowEmitsCreatedProperty() = runBlocking {
        withTimeout(10_000) {
            val repository = PropertyRepository(database, PhotoStorage(File(context.cacheDir, "inventory-database-test")))
            val initial = CompletableDeferred<Unit>()
            val created = CompletableDeferred<PropertyEntity>()
            val collector = launch {
                repository.properties.collect { properties ->
                    initial.complete(Unit)
                    properties.firstOrNull { it.name == "Nueva propiedad" }?.let { created.complete(it) }
                }
            }
            try {
                initial.await()
                repository.create("  Nueva propiedad  ", "  Calle 2  ")
                val property = created.await()
                assertEquals("Calle 2", property.address)
                assertEquals(PropertyStatus.PENDIENTE, property.status)
                assertEquals(2, database.propertyDao().getAll().size)
            } finally {
                collector.cancelAndJoin()
            }
        }
    }

    fun testRepositoryRejectsBlankFields() = runBlocking {
        val repository = PropertyRepository(database, PhotoStorage(File(context.cacheDir, "inventory-database-test")))
        for ((name, address) in listOf("" to "Calle 1", "   " to "Calle 1", "Nombre" to "", "Nombre" to "   ")) {
            try {
                repository.create(name, address)
                fail("Debe rechazar campos vacíos")
            } catch (_: IllegalArgumentException) {
                assertEquals(1, database.propertyDao().getAll().size)
            }
        }
    }

    fun testPropertyStatusesRoundTrip() = runBlocking {
        for (status in PropertyStatus.entries) {
            val id = database.propertyDao().insert(PropertyEntity(name = status.name, address = "Calle 1", status = status))
            assertEquals(status, database.propertyDao().getById(id)!!.status)
        }
    }

    fun testViewModelValidatesAndPreservesForm() {
        onMain {
            val store = ViewModelStore()
            val viewModel = PropertyViewModel(PropertyRepository(database, PhotoStorage(File(context.cacheDir, "inventory-database-test"))))
            store.put("properties", viewModel)
            try {
                viewModel.openCreateDialog()
                viewModel.updateName("Departamento")
                viewModel.updateAddress("   ")
                viewModel.createProperty()
                assertNotNull(viewModel.form.error)
                assertEquals("Departamento", viewModel.form.name)
                assertFalse(viewModel.form.isSaving)
                viewModel.dismissCreateDialog()
                assertFalse(viewModel.form.isOpen)
            } finally {
                store.clear()
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun testTypedRoomsRouteDeclaresLongPropertyId() {
        val descriptor = RoomsRoute.serializer().descriptor
        assertEquals(1, descriptor.elementsCount)
        assertEquals("propertyId", descriptor.getElementName(0))
        assertEquals(PrimitiveKind.LONG, descriptor.getElementDescriptor(0).kind)
    }

    private fun onMain(action: () -> Unit) {
        val task = FutureTask(action, Unit)
        Handler(Looper.getMainLooper()).post(task)
        task.get(10, TimeUnit.SECONDS)
    }
}
