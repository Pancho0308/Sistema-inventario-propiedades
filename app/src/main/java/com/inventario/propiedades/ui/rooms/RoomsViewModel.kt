package com.inventario.propiedades.ui.rooms

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventario.propiedades.data.RoomRepository
import com.inventario.propiedades.data.local.RoomWithProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RoomsUiState(
    val rooms: List<RoomWithProgress> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

data class RoomFormState(
    val isOpen: Boolean = false,
    val name: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
)

class RoomsViewModel(private val repository: RoomRepository, private val propertyId: Long) : ViewModel() {
    val uiState = repository.observeRooms(propertyId)
        .map { RoomsUiState(rooms = it, isLoading = false) }
        .catch { emit(RoomsUiState(isLoading = false, error = "No se pudieron cargar las habitaciones.")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoomsUiState())

    var form by mutableStateOf(RoomFormState())
        private set

    fun openCreateDialog() {
        if (!form.isSaving) form = RoomFormState(isOpen = true)
    }

    fun dismissCreateDialog() {
        if (!form.isSaving) form = RoomFormState()
    }

    fun updateName(name: String) {
        if (!form.isSaving) form = form.copy(name = name, error = null)
    }

    fun createRoom() {
        if (form.isSaving || !form.isOpen) return
        val name = form.name
        if (name.isBlank()) {
            form = form.copy(error = "Escribe el nombre de la habitación.")
            return
        }
        form = form.copy(isSaving = true, error = null)
        viewModelScope.launch {
            try {
                repository.create(propertyId, name)
                form = RoomFormState()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                form = form.copy(isSaving = false, error = "No se pudo guardar. Intenta nuevamente.")
            }
        }
    }
}
