package com.inventario.propiedades

import android.database.sqlite.SQLiteConstraintException
import com.inventario.propiedades.data.ItemRepository
import com.inventario.propiedades.data.PropertyRepository
import com.inventario.propiedades.data.RoomRepository
import com.inventario.propiedades.data.local.ItemDao
import com.inventario.propiedades.data.local.ItemEntity
import com.inventario.propiedades.data.local.ItemStatus
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking

class PhotoRepositoryTest : DatabaseTestCase() {
    private suspend fun photographedItem(roomId: Long): ItemEntity {
        val photo = photoStorage.saveCapture(createCapture())
        val item = ItemEntity(roomId = roomId, name = "Mesa", status = ItemStatus.DAÑADO, note = "Nota", photoPath = photo.path)
        return item.copy(id = database.itemDao().insert(item))
    }

    fun testReplacementKeepsOldFileUntilNewFileIsRegistered() = runBlocking {
        val roomId = createRoom(createProperty())
        val item = photographedItem(roomId)
        val old = File(item.photoPath!!)
        val actual = database.itemDao()
        val dao = object : ItemDao by actual {
            override suspend fun updatePhotoPath(roomId: Long, itemId: Long, path: String?): Int {
                assertTrue(old.isFile)
                assertTrue(File(path!!).isFile)
                assertEquals(item.photoPath, actual.getById(itemId)!!.photoPath)
                return actual.updatePhotoPath(roomId, itemId, path)
            }
        }
        ItemRepository(database, photoStorage, dao).savePhoto(roomId, item.id, createCapture())
        val saved = actual.getById(item.id)!!
        assertFalse(old.exists())
        assertTrue(File(saved.photoPath!!).isFile)
        assertEquals(item.copy(photoPath = saved.photoPath), saved)
    }

    fun testFailedRegistrationPreservesPreviousPhotoAndRemovesNewFile() = runBlocking {
        val roomId = createRoom(createProperty())
        val item = photographedItem(roomId)
        val actual = database.itemDao()
        val dao = object : ItemDao by actual {
            override suspend fun updatePhotoPath(roomId: Long, itemId: Long, path: String?): Int {
                error("Error de prueba")
            }
        }
        val capture = createCapture()
        val filesBefore = photoStorage.directory.list()!!.toSet()
        try {
            ItemRepository(database, photoStorage, dao).savePhoto(roomId, item.id, capture)
            fail("Debe propagar el error de Room")
        } catch (_: IllegalStateException) {
            assertEquals(item, actual.getById(item.id))
            assertTrue(File(item.photoPath!!).isFile)
            assertEquals(filesBefore, photoStorage.directory.list()!!.toSet())
        }
    }

    fun testWrongRoomOrDeletedItemDoesNotLeakNewPhoto() = runBlocking {
        val roomId = createRoom(createProperty())
        val item = photographedItem(roomId)
        val capture = createCapture()
        val filesBefore = photoStorage.directory.list()!!.toSet()
        val repository = ItemRepository(database, photoStorage)
        for ((room, id) in listOf(Long.MAX_VALUE to item.id, roomId to Long.MAX_VALUE)) {
            try {
                repository.savePhoto(room, id, capture)
                fail("Debe rechazar el artículo inexistente o de otra habitación")
            } catch (_: IllegalStateException) {
                assertEquals(filesBefore, photoStorage.directory.list()!!.toSet())
                assertEquals(item, database.itemDao().getById(item.id))
            }
        }
    }

    fun testRemovePhotoClearsOnlyPathAndDeletesFile() = runBlocking {
        val roomId = createRoom(createProperty())
        val item = photographedItem(roomId)
        ItemRepository(database, photoStorage).removePhoto(roomId, item.id)
        assertEquals(item.copy(photoPath = null), database.itemDao().getById(item.id))
        assertFalse(File(item.photoPath!!).exists())
    }

    fun testDeleteItemPreservesOtherPhoto() = runBlocking {
        val roomId = createRoom(createProperty())
        val item = photographedItem(roomId)
        val other = photographedItem(roomId)
        ItemRepository(database, photoStorage).delete(roomId, item.id)
        assertNull(database.itemDao().getById(item.id))
        assertFalse(File(item.photoPath!!).exists())
        assertTrue(File(other.photoPath!!).exists())
    }

    fun testDeleteRoomCollectsPhotoPathsBeforeCascade() = runBlocking {
        val propertyId = createProperty()
        val roomId = createRoom(propertyId)
        val first = photographedItem(roomId)
        val second = photographedItem(roomId)
        val other = photographedItem(createRoom(propertyId, "Otra"))
        RoomRepository(database, photoStorage).delete(roomId)
        assertNull(database.roomDao().getById(roomId))
        assertNull(database.itemDao().getById(first.id))
        assertFalse(File(first.photoPath!!).exists())
        assertFalse(File(second.photoPath!!).exists())
        assertTrue(File(other.photoPath!!).exists())
    }

    fun testDeletePropertyCollectsPhotoPathsAcrossAllRooms() = runBlocking {
        val propertyId = createProperty()
        val first = photographedItem(createRoom(propertyId))
        val second = photographedItem(createRoom(propertyId, "Otra"))
        val other = photographedItem(createRoom(createProperty()))
        PropertyRepository(database, photoStorage).delete(propertyId)
        assertNull(database.propertyDao().getById(propertyId))
        assertTrue(database.roomDao().getByProperty(propertyId).isEmpty())
        assertNull(database.itemDao().getById(first.id))
        assertNull(database.itemDao().getById(second.id))
        assertFalse(File(first.photoPath!!).exists())
        assertFalse(File(second.photoPath!!).exists())
        assertTrue(File(other.photoPath!!).exists())
    }

    fun testFailedCascadeDoesNotDeleteAnyPhoto() = runBlocking {
        val propertyId = createProperty()
        val roomId = createRoom(propertyId)
        val item = photographedItem(roomId)
        database.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER fail_delete BEFORE DELETE ON items
            BEGIN SELECT RAISE(ABORT, 'Error de prueba'); END
        """.trimIndent())
        val operations: List<suspend () -> Unit> = listOf(
            { ItemRepository(database, photoStorage).delete(roomId, item.id) },
            { RoomRepository(database, photoStorage).delete(roomId) },
            { PropertyRepository(database, photoStorage).delete(propertyId) },
        )
        for (operation in operations) {
            try {
                operation()
                fail("Debe propagar el fallo del borrado")
            } catch (_: SQLiteConstraintException) {
                assertEquals(item, database.itemDao().getById(item.id))
                assertTrue(File(item.photoPath!!).isFile)
                assertNotNull(database.roomDao().getById(roomId))
                assertNotNull(database.propertyDao().getById(propertyId))
            }
        }
    }

    fun testCascadeNeverDeletesAnExternalPath() = runBlocking {
        val roomId = createRoom(createProperty())
        val outside = File(photoStorage.directory.parentFile, "outside.jpg").apply { writeText("No borrar") }
        database.itemDao().insert(ItemEntity(roomId = roomId, name = "Mesa", photoPath = outside.path))
        RoomRepository(database, photoStorage).delete(roomId)
        assertTrue(outside.exists())
    }

    fun testCleanupFailureNeverRemovesTheRegisteredNewPhoto() = runBlocking {
        val roomId = createRoom(createProperty())
        val capture = createCapture()
        val invalidOld = File(photoStorage.directory, "folder").apply { mkdir() }
        val id = database.itemDao().insert(ItemEntity(roomId = roomId, name = "Mesa", photoPath = invalidOld.path))
        try {
            ItemRepository(database, photoStorage).savePhoto(roomId, id, capture)
            fail("Debe informar que no se pudo eliminar el archivo anterior")
        } catch (_: IOException) {
            val saved = database.itemDao().getById(id)!!
            assertTrue(saved.photoPath != invalidOld.path)
            assertTrue(File(saved.photoPath!!).isFile)
            assertTrue(invalidOld.isDirectory)
        }
    }
}
