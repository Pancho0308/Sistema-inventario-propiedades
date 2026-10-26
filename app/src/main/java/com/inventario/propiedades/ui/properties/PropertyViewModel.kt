package com.inventario.propiedades.ui.properties

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventario.propiedades.data.PropertyRepository
import com.inventario.propiedades.data.local.PropertyEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PropertiesUiState(
    val properties: List<PropertyEntity> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

data class PropertyFormState(
    val isOpen: Boolean = false,
    val name: String = "",
    val address: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
)

class PropertyViewModel(private val repository: PropertyRepository) : ViewModel() {
    val uiState = repository.properties
        .map { PropertiesUiState(properties = it, isLoading = false) }
        .catch { emit(PropertiesUiState(isLoading = false, error = "No se pudieron cargar las propiedades.")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PropertiesUiState())

    var form by mutableStateOf(PropertyFormState())
        private set

    fun openCreateDialog() {
        form = PropertyFormState(isOpen = true)
    }

    fun dismissCreateDialog() {
        if (!form.isSaving) form = PropertyFormState()
    }

    fun updateName(name: String) {
        if (!form.isSaving) form = form.copy(name = name, error = null)
    }

    fun updateAddress(address: String) {
        if (!form.isSaving) form = form.copy(address = address, error = null)
    }

    fun createProperty() {
        if (form.isSaving || !form.isOpen) return
        val submitted = form
        if (submitted.name.isBlank() || submitted.address.isBlank()) {
            form = form.copy(error = "Completa el nombre y la dirección.")
            return
        }
        form = form.copy(isSaving = true, error = null)
        viewModelScope.launch {
            try {
                repository.create(submitted.name, submitted.address)
                form = PropertyFormState()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Conserva lo escrito si falla el guardado para permitir otro intento.
                form = form.copy(isSaving = false, error = "No se pudo guardar. Intenta nuevamente.")
            }
        }
    }
}
