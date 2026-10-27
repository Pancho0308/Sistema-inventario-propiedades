package com.inventario.propiedades.ui.templates

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventario.propiedades.data.TemplateRepository
import com.inventario.propiedades.data.local.TemplateEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TemplatesUiState(
    val templates: List<TemplateEntity> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

class TemplatesViewModel(private val repository: TemplateRepository) : ViewModel() {
    val uiState = repository.templates
        .map { TemplatesUiState(templates = it, isLoading = false) }
        .catch { emit(TemplatesUiState(isLoading = false, error = "No se pudieron cargar las plantillas.")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TemplatesUiState())

    var pendingDelete by mutableStateOf<TemplateEntity?>(null)
        private set
    var isDeleting by mutableStateOf(false)
        private set
    var deleteError by mutableStateOf<String?>(null)
        private set

    fun requestDelete(template: TemplateEntity) {
        if (template.isBuiltIn || isDeleting) return
        pendingDelete = template
        deleteError = null
    }

    fun dismissDelete() {
        if (!isDeleting) {
            pendingDelete = null
            deleteError = null
        }
    }

    fun deleteConfirmed() {
        val template = pendingDelete ?: return
        if (isDeleting || template.isBuiltIn) return
        isDeleting = true
        deleteError = null
        viewModelScope.launch {
            try {
                repository.delete(template.id)
                pendingDelete = null
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                deleteError = "No se pudo eliminar la plantilla. Intenta nuevamente."
            } finally {
                isDeleting = false
            }
        }
    }
}
