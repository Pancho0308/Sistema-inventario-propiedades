@file:Suppress("DEPRECATION")

package com.inventario.propiedades

import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import android.os.Handler
import android.os.Looper
import android.test.AndroidTestCase
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import com.inventario.propiedades.data.PhotoStorage
import java.io.File
import java.util.UUID
import com.inventario.propiedades.data.local.InventoryDatabase
import com.inventario.propiedades.data.local.PropertyEntity
import com.inventario.propiedades.data.local.RoomEntity
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

// Cada prueba usa una base en memoria y nunca abre inventory.db.
abstract class DatabaseTestCase : AndroidTestCase() {
    protected lateinit var database: InventoryDatabase
    protected lateinit var photoStorage: PhotoStorage
    private lateinit var testFiles: File
    private val models = ViewModelStore()
    private var modelId = 0

    override fun setUp() {
        super.setUp()
        database = Room.inMemoryDatabaseBuilder(context, InventoryDatabase::class.java).build()
        testFiles = File(context.cacheDir, "photo-test-${UUID.randomUUID()}").apply { mkdirs() }
        photoStorage = PhotoStorage(testFiles)
    }

    override fun tearDown() {
        try {
            onMain { models.clear() }
            if (::database.isInitialized) database.close()
            if (::testFiles.isInitialized) testFiles.deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    protected fun createCapture(width: Int = 320, height: Int = 240, orientation: Int = ExifInterface.ORIENTATION_NORMAL): File {
        val file = photoStorage.createCaptureFile()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.BLUE)
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
        } finally {
            bitmap.recycle()
        }
        ExifInterface(file.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
        return file
    }

    protected suspend fun createProperty(): Long =
        database.propertyDao().insert(PropertyEntity(name = "Prueba", address = "Calle 1"))

    protected suspend fun createRoom(propertyId: Long, name: String = "Cocina"): Long =
        database.roomDao().insert(RoomEntity(propertyId = propertyId, name = name))

    protected fun <T> onMain(action: () -> T): T {
        val task = FutureTask(Callable { action() })
        Handler(Looper.getMainLooper()).post(task)
        return task.get(10, TimeUnit.SECONDS)
    }

    protected fun <T : ViewModel> createViewModel(factory: () -> T): T = onMain {
        factory().also { models.put("model-${modelId++}", it) }
    }

    protected suspend fun awaitMain(condition: () -> Boolean) = withTimeout(10_000) {
        while (!onMain(condition)) delay(10)
    }

    protected suspend fun <T> Flow<T>.await(predicate: (T) -> Boolean): T =
        withTimeout(10_000) { first(predicate) }
}
