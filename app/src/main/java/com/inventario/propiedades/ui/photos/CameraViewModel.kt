package com.inventario.propiedades.ui.photos

import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventario.propiedades.data.ItemRepository
import com.inventario.propiedades.data.PhotoStorage
import java.io.File
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class CameraViewModel(
    private val repository: ItemRepository,
    private val photos: PhotoStorage,
    private val roomId: Long,
    private val itemId: Long,
) : ViewModel() {
    var isSaving by mutableStateOf(false)
        private set
    var isSaved by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun capture(imageCapture: ImageCapture, executor: Executor) {
        if (isSaving || isSaved) return
        isSaving = true
        error = null
        viewModelScope.launch {
            var capture: File? = null
            var saved = false
            try {
                val file = withContext(Dispatchers.IO) { photos.createCaptureFile().also { capture = it } }
                takePhoto(imageCapture, executor, file)
                repository.savePhoto(roomId, itemId, file)
                saved = true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                error = "No se pudo completar el guardado. Puedes volver al checklist para revisar o reintentar."
            } finally {
                try {
                    withContext(Dispatchers.IO + NonCancellable) { photos.delete(capture?.path) }
                } catch (_: Exception) {
                    error = "No se pudo limpiar la captura temporal. Vuelve al checklist para revisar la foto."
                }
                isSaving = false
            }
            isSaved = saved && error == null
        }
    }

    private suspend fun takePhoto(imageCapture: ImageCapture, executor: Executor, file: File) =
        suspendCancellableCoroutine<Unit> { continuation ->
            imageCapture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), executor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        if (continuation.isActive) continuation.resume(Unit)
                        else runCatching { photos.delete(file.path) }
                    }
                    override fun onError(exception: ImageCaptureException) {
                        if (continuation.isActive) continuation.resumeWithException(exception)
                        else runCatching { photos.delete(file.path) }
                    }
                })
        }
}
