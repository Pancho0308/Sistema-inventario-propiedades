package com.inventario.propiedades

import android.os.Looper
import android.system.Os
import com.inventario.propiedades.data.ItemRepository
import com.inventario.propiedades.data.local.ItemDao
import com.inventario.propiedades.data.local.ItemEntity
import java.io.File
import kotlinx.coroutines.runBlocking

class PhotoCleanupTest : DatabaseTestCase() {
    fun testKeepsReferencedPhotosAndRemovesOrphansAndTemporaryCaptures() = runBlocking {
        val roomId = createRoom(createProperty())
        val first = photoStorage.saveCapture(createCapture())
        val second = photoStorage.saveCapture(createCapture())
        database.itemDao().insert(ItemEntity(roomId = roomId, name = "Mesa", photoPath = first.path))
        database.itemDao().insert(ItemEntity(roomId = createRoom(createProperty()), name = "Cama", photoPath = second.path))
        val orphan = photoStorage.saveCapture(createCapture())
        val temporary = photoStorage.createCaptureFile()
        val outside = File(photoStorage.directory.parentFile, "outside.jpg").apply { writeText("No borrar") }
        Os.symlink(outside.path, File(photoStorage.directory, "external-link.jpg").path)
        ItemRepository(database, photoStorage).cleanupUnusedPhotos()
        assertTrue(first.isFile)
        assertTrue(second.isFile)
        assertFalse(orphan.exists())
        assertFalse(temporary.exists())
        assertTrue(outside.isFile)
        assertEquals(first.path, database.itemDao().getByRoom(roomId).single().photoPath)
    }

    fun testNoReferencesRemovesOnlyFilesInsideOwnDirectory() = runBlocking {
        val file = createCapture()
        val folder = File(photoStorage.directory, "folder").apply { mkdir() }
        val outside = File(photoStorage.directory.parentFile, "outside.jpg").apply { writeText("No borrar") }
        ItemRepository(database, photoStorage).cleanupUnusedPhotos()
        assertFalse(file.exists())
        assertTrue(folder.isDirectory)
        assertTrue(outside.isFile)
    }

    fun testMissingPhotoDirectoryNeedsNoCreation() = runBlocking {
        assertFalse(photoStorage.directory.exists())
        ItemRepository(database, photoStorage).cleanupUnusedPhotos()
        assertFalse(photoStorage.directory.exists())
    }

    fun testDatabaseFailureNeverDeletesFiles() = runBlocking {
        val capture = createCapture()
        val dao = object : ItemDao by database.itemDao() {
            override suspend fun getAllPhotoPaths(): List<String> = error("Error de lectura de prueba")
        }
        try {
            ItemRepository(database, photoStorage, dao).cleanupUnusedPhotos()
            fail("Debe propagar el error sin interpretar que no hay referencias")
        } catch (_: IllegalStateException) { assertTrue(capture.isFile) }
    }

    fun testCleanupReadsReferencesOffMainThread() = runBlocking {
        var queried = false
        val dao = object : ItemDao by database.itemDao() {
            override suspend fun getAllPhotoPaths(): List<String> {
                assertFalse(Looper.getMainLooper().isCurrentThread)
                queried = true
                return emptyList()
            }
        }
        ItemRepository(database, photoStorage, dao).cleanupUnusedPhotos()
        assertTrue(queried)
    }
}
