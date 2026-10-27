package com.inventario.propiedades.ui.properties

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inventario.propiedades.data.local.PropertyStatus
import com.inventario.propiedades.data.local.TemplateEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertiesScreen(viewModel: PropertyViewModel, onPropertyClick: (Long) -> Unit, onTemplates: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val error = state.error
    Scaffold(
        topBar = { TopAppBar(title = { Text("Propiedades") }, actions = {
            TextButton(onClick = onTemplates) { Text("Plantillas") }
        }) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::openCreateDialog,
                modifier = Modifier.size(56.dp).semantics { contentDescription = "Crear propiedad" },
            ) {
                Text("+", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.clearAndSetSemantics { })
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.semantics { contentDescription = "Cargando propiedades" })
            }
            error != null -> Box(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                })
            }
            state.properties.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text("Aún no hay propiedades. Toca + para crear la primera.")
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.properties, key = { it.id }) { property ->
                    Card(
                        onClick = { onPropertyClick(property.id) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 104.dp),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(property.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                PropertyMenu(property.id, property.name, viewModel)
                            }
                            Text(property.address, style = MaterialTheme.typography.bodyMedium)
                            PropertyStatusChip(property.status)
                        }
                    }
                }
            }
        }
    }
    if (viewModel.form.isOpen) CreatePropertyDialog(viewModel, state.templates)
    if (viewModel.templateForm.propertyId != null) SaveTemplateDialog(viewModel)
}

@Composable
private fun PropertyStatusChip(status: PropertyStatus) {
    val (background, foreground) = when (status) {
        PropertyStatus.PENDIENTE -> Color(0xFFFFF3CD) to Color(0xFF664D03)
        PropertyStatus.EN_CURSO -> Color(0xFFDCEBFF) to Color(0xFF123F7A)
        PropertyStatus.LISTO -> Color(0xFFDDF3E4) to Color(0xFF185B35)
    }
    // El chip informa un estado; no agrega una acción táctil sin utilidad.
    Surface(shape = CircleShape, color = background, contentColor = foreground) {
        Text(
            status.name.replace('_', ' '),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun CreatePropertyDialog(viewModel: PropertyViewModel, templates: List<TemplateEntity>) {
    val form = viewModel.form
    val isClone = form.sourceId != null
    AlertDialog(
        onDismissRequest = viewModel::dismissCreateDialog,
        title = { Text(if (isClone) "Clonar propiedad" else "Nueva propiedad") },
        text = {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!isClone) TemplatePicker(templates, form.templateId, !form.isSaving, viewModel::selectTemplate)
                OutlinedTextField(
                    value = form.name,
                    onValueChange = viewModel::updateName,
                    label = { Text("Nombre") },
                    singleLine = true,
                    enabled = !form.isSaving,
                    isError = form.error != null && form.name.isBlank(),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = form.address,
                    onValueChange = viewModel::updateAddress,
                    label = { Text("Dirección") },
                    singleLine = true,
                    enabled = !form.isSaving,
                    isError = form.error != null && form.address.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { viewModel.createProperty() }),
                )
                form.error?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                    })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = viewModel::createProperty,
                enabled = !form.isSaving && form.name.isNotBlank() && form.address.isNotBlank(),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(if (form.isSaving) "Guardando…" else if (isClone) "Clonar" else "Crear")
            }
        },
        dismissButton = {
            TextButton(
                onClick = viewModel::dismissCreateDialog,
                enabled = !form.isSaving,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("Cancelar")
            }
        },
    )
}

@Composable
private fun PropertyMenu(propertyId: Long, name: String, viewModel: PropertyViewModel) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Opciones de $name")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Clonar") }, onClick = {
                expanded = false
                viewModel.openCloneDialog(propertyId)
            })
            DropdownMenuItem(text = { Text("Guardar como plantilla") }, onClick = {
                expanded = false
                viewModel.openTemplateDialog(propertyId)
            })
        }
    }
}

@Composable
private fun TemplatePicker(templates: List<TemplateEntity>, selectedId: Long?, enabled: Boolean, onSelect: (Long?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = if (selectedId == null) "Empezar en blanco"
        else templates.firstOrNull { it.id == selectedId }?.name ?: "Plantilla no disponible"
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Estructura", style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(selected) }
            DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("Empezar en blanco") }, onClick = { expanded = false; onSelect(null) })
                templates.forEach { template ->
                    DropdownMenuItem(text = { Text(template.name + if (template.isBuiltIn) " · Base" else " · Propia") },
                        onClick = { expanded = false; onSelect(template.id) })
                }
            }
        }
    }
}

@Composable
private fun SaveTemplateDialog(viewModel: PropertyViewModel) {
    val form = viewModel.templateForm
    AlertDialog(
        onDismissRequest = viewModel::dismissTemplateDialog,
        title = { Text("Guardar como plantilla") },
        text = {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = form.name, onValueChange = viewModel::updateTemplateName,
                    label = { Text("Nombre de la plantilla") }, enabled = !form.isSaving,
                    singleLine = true, isError = form.error != null,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { viewModel.saveTemplate() }))
                Text("Se guardarán solo las habitaciones y nombres de artículos, sin estados, notas ni fotos.",
                    style = MaterialTheme.typography.bodySmall)
                form.error?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::saveTemplate, enabled = !form.isSaving && form.name.isNotBlank()) {
                Text(if (form.isSaving) "Guardando…" else "Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissTemplateDialog, enabled = !form.isSaving) { Text("Cancelar") }
        },
    )
}
