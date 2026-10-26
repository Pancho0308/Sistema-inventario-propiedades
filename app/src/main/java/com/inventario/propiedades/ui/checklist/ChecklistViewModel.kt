package com.inventario.propiedades.ui.checklist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventario.propiedades.data.ItemRepository
import com.inventario.propiedades.data.local.ItemEntity
import com.inventario.propiedades.data.local.ItemStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ChecklistUiState(
    val items: List<ItemEntity> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

class ChecklistViewModel(private val repository: ItemRepository, private val roomId: Long) : ViewModel() {
    val uiState = repository.observeItems(roomId)
        .map { ChecklistUiState(items = it, isLoading = false) }
        .catch { emit(ChecklistUiState(isLoading = false, error = "No se pudieron cargar los artículos.")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChecklistUiState())

    var newItemName by mutableStateOf("")
        private set
    var isAdding by mutableStateOf(false)
        private set
    var addError by mutableStateOf<String?>(null)
        private set
    var saveError by mutableStateOf<String?>(null)
        private set

    private val statusMutex = Mutex()

    fun updateNewItemName(name: String) {
        newItemName = name
        addError = null
    }

    fun addItem() {
        if (isAdding) return
        val name = newItemName
        if (name.isBlank()) {
            addError = "Escribe el nombre del artículo."
            return
        }
        isAdding = true
        addError = null
        viewModelScope.launch {
            try {
                repository.create(roomId, name)
                // No borra texto que el usuario haya empezado a escribir durante el guardado.
                if (newItemName == name) newItemName = ""
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                addError = "No se pudo agregar. Intenta nuevamente."
            } finally {
                isAdding = false
            }
        }
    }

    fun selectStatus(itemId: Long, status: ItemStatus) {
        saveError = null
        viewModelScope.launch {
            // Mantiene el orden de los toques para que prevalezca la última selección.
            statusMutex.withLock {
                try {
                    repository.setStatus(roomId, itemId, status)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    saveError = "No se pudo guardar el estado. Vuelve a tocar el botón para reintentar."
                }
            }
        }
    }
}
