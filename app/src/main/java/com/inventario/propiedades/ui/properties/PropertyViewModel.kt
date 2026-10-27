package com.inventario.propiedades.ui.properties

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventario.propiedades.data.PropertyRepository
import com.inventario.propiedades.data.TemplateRepository
import com.inventario.propiedades.data.local.TemplateEntity
import com.inventario.propiedades.data.local.PropertyEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PropertiesUiState(
    val properties: List<PropertyEntity> = emptyList(),
    val templates: List<TemplateEntity> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

data class PropertyFormState(
    val isOpen: Boolean = false,
    val sourceId: Long? = null,
    val templateId: Long? = null,
    val name: String = "",
    val address: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
)

data class TemplateFormState(
    val propertyId: Long? = null,
    val name: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
)

class PropertyViewModel(private val repository: PropertyRepository, private val templateRepository: TemplateRepository) : ViewModel() {
    val uiState = repository.properties
        .combine(templateRepository.templates) { properties, templates ->
            PropertiesUiState(properties = properties, templates = templates, isLoading = false)
        }
        .catch { emit(PropertiesUiState(isLoading = false, error = "No se pudieron cargar las propiedades o plantillas.")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PropertiesUiState())

    var form by mutableStateOf(PropertyFormState())
        private set
    var templateForm by mutableStateOf(TemplateFormState())
        private set

    fun openCreateDialog() {
        if (form.isSaving || templateForm.isSaving) return
        templateForm = TemplateFormState()
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

    fun openCloneDialog(propertyId: Long) {
        if (form.isSaving || templateForm.isSaving) return
        templateForm = TemplateFormState()
        form = PropertyFormState(isOpen = true, sourceId = propertyId)
    }

    fun selectTemplate(templateId: Long?) {
        if (!form.isSaving && form.sourceId == null) form = form.copy(templateId = templateId, error = null)
    }

    fun openTemplateDialog(propertyId: Long) {
        if (form.isSaving || templateForm.isSaving) return
        form = PropertyFormState()
        templateForm = TemplateFormState(propertyId = propertyId)
    }

    fun dismissTemplateDialog() {
        if (!templateForm.isSaving) templateForm = TemplateFormState()
    }

    fun updateTemplateName(name: String) {
        if (!templateForm.isSaving) templateForm = templateForm.copy(name = name, error = null)
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
                if (submitted.sourceId != null) repository.clone(submitted.sourceId, submitted.name, submitted.address)
                else repository.create(submitted.name, submitted.address, submitted.templateId)
                form = PropertyFormState()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Conserva lo escrito si falla el guardado para permitir otro intento.
                form = form.copy(isSaving = false, error = "No se pudo guardar. Intenta nuevamente.")
            }
        }
    }

    fun saveTemplate() {
        val submitted = templateForm
        val propertyId = submitted.propertyId ?: return
        if (submitted.isSaving) return
        if (submitted.name.isBlank()) {
            templateForm = submitted.copy(error = "Escribe el nombre de la plantilla.")
            return
        }
        templateForm = submitted.copy(isSaving = true, error = null)
        viewModelScope.launch {
            try {
                templateRepository.saveFromProperty(propertyId, submitted.name)
                templateForm = TemplateFormState()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                templateForm = templateForm.copy(isSaving = false, error = "No se pudo guardar la plantilla. Intenta nuevamente.")
            }
        }
    }
}
