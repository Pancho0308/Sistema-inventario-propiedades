package com.inventario.propiedades

import android.app.Application
import com.inventario.propiedades.data.ItemRepository
import com.inventario.propiedades.data.PhotoStorage
import com.inventario.propiedades.data.local.InventoryDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StartupState(val isReady: Boolean = false, val error: String? = null)

class InventoryApplication : Application() {
    val database by lazy { InventoryDatabase.getInstance(this) }
    val photos by lazy { PhotoStorage(filesDir) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableStartup = MutableStateFlow(StartupState())
    val startup = mutableStartup.asStateFlow()
    private var startupJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        initialize()
    }

    fun initialize() {
        if (startupJob?.isActive == true || startup.value.isReady) return
        mutableStartup.value = StartupState()
        startupJob = scope.launch {
            try {
                // Se ejecuta una vez por proceso, antes de permitir nuevas capturas.
                ItemRepository(database, photos).cleanupUnusedPhotos()
                mutableStartup.value = StartupState(isReady = true)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableStartup.value = StartupState(error = "No se pudo preparar el inventario. No se abrirá hasta completar la revisión de fotos.")
            }
        }
    }
}
