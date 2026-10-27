package com.inventario.propiedades.ui.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(viewModel: TemplatesViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val error = state.error
    Scaffold(topBar = {
        TopAppBar(title = { Text("Plantillas") }, navigationIcon = {
            TextButton(onClick = onBack) { Text("Volver") }
        })
    }) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null -> Text(error, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(padding).padding(24.dp))
            state.templates.isEmpty() -> Text("Aún no hay plantillas.", modifier = Modifier.padding(padding).padding(24.dp))
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.templates, key = { it.id }) { template ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(template.name, style = MaterialTheme.typography.titleMedium)
                                Text(if (template.isBuiltIn) "Base · no se puede eliminar" else "Propia",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            if (!template.isBuiltIn) {
                                TextButton(onClick = { viewModel.requestDelete(template) }) { Text("Eliminar") }
                            }
                        }
                    }
                }
            }
        }
    }
    val pending = viewModel.pendingDelete
    if (pending != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("Eliminar plantilla") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Se eliminará «${pending.name}». Las propiedades creadas no se modificarán.")
                    viewModel.deleteError?.let { error ->
                        Text(error, color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::deleteConfirmed, enabled = !viewModel.isDeleting) {
                    Text(if (viewModel.isDeleting) "Eliminando…" else "Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete, enabled = !viewModel.isDeleting) { Text("Cancelar") }
            },
        )
    }
}
