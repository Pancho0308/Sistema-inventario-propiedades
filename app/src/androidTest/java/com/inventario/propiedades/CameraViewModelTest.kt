package com.inventario.propiedades

import androidx.camera.core.ImageCapture
import androidx.core.content.ContextCompat
import com.inventario.propiedades.data.ItemRepository
import com.inventario.propiedades.data.local.ItemEntity
import com.inventario.propiedades.ui.navigation.CameraRoute
import com.inventario.propiedades.ui.photos.CameraViewModel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PrimitiveKind

class CameraViewModelTest : DatabaseTestCase() {
    fun testUnboundCameraErrorCleansTemporaryFileAndAllowsRetry() = runBlocking {
        val roomId = createRoom(createProperty())
        val old = photoStorage.saveCapture(createCapture())
        val itemId = database.itemDao().insert(ItemEntity(roomId = roomId, name = "Mesa", photoPath = old.path))
        val initialFiles = photoStorage.directory.list()!!.toSet()
        val model = createViewModel { CameraViewModel(ItemRepository(database, photoStorage), photoStorage, roomId, itemId) }
        val imageCapture = onMain { ImageCapture.Builder().build() }
        repeat(2) {
            onMain {
                model.capture(imageCapture, ContextCompat.getMainExecutor(context))
                model.capture(imageCapture, ContextCompat.getMainExecutor(context))
                assertTrue(model.isSaving)
            }
            awaitMain { !model.isSaving && model.error != null }
            onMain { assertFalse(model.isSaved) }
            assertEquals(initialFiles, photoStorage.directory.list()!!.toSet())
            assertEquals(old.path, database.itemDao().getById(itemId)!!.photoPath)
            assertTrue(old.isFile)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun testCameraRouteCarriesRoomAndItemIdsAsLong() {
        val descriptor = CameraRoute.serializer().descriptor
        assertEquals(2, descriptor.elementsCount)
        assertEquals("roomId", descriptor.getElementName(0))
        assertEquals("itemId", descriptor.getElementName(1))
        repeat(2) { assertEquals(PrimitiveKind.LONG, descriptor.getElementDescriptor(it).kind) }
    }
}
