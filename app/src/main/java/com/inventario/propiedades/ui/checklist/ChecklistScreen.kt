package com.inventario.propiedades.ui.checklist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inventario.propiedades.data.local.ItemEntity
import com.inventario.propiedades.data.local.ItemStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistScreen(viewModel: ChecklistViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val error = state.error
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Checklist") },
            navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("Volver") } },
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            TextButton(
                onClick = viewModel::markAllAsGood,
                enabled = !state.isLoading && error == null && !viewModel.isMarkingAll && state.items.any { it.status == null },
                modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
            ) { Text(if (viewModel.isMarkingAll) "Guardando…" else "Marcar todo como Bueno") }
            viewModel.saveError?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp).semantics {
                    liveRegion = LiveRegionMode.Polite
                })
            }
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.semantics { contentDescription = "Cargando artículos" })
                }
                error != null -> Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(state.items, key = { it.id }) { item ->
                        ItemChecklistCard(item, onStatusClick = { viewModel.selectStatus(item.id, it) })
                    }
                    item(key = "add-item") { AddItemField(viewModel) }
                }
            }
        }
    }
}

@Composable
private fun ItemChecklistCard(item: ItemEntity, onStatusClick: (ItemStatus) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (item.status == null) {
                    Text("• Sin revisar", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
                Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ItemStatus.entries.forEach { status ->
                        val isSelected = item.status == status
                        val color = when (status) {
                            ItemStatus.NUEVO -> Color(0xFF1565C0)
                            ItemStatus.BUENO -> Color(0xFF2E7D32)
                            ItemStatus.DESGASTADO -> Color(0xFF8D6E00)
                            ItemStatus.DAÑADO -> Color(0xFFC62828)
                            ItemStatus.FALTANTE -> Color(0xFF616161)
                        }
                        val icon = when (status) {
                            ItemStatus.NUEVO -> Icons.Default.Star
                            ItemStatus.BUENO -> Icons.Default.Check
                            ItemStatus.DESGASTADO -> Icons.Default.Warning
                            ItemStatus.DAÑADO -> Icons.Default.Close
                            ItemStatus.FALTANTE -> Icons.Default.Search
                        }
                        Surface(
                            selected = isSelected,
                            onClick = { onStatusClick(status) },
                            modifier = (if (isSelected) Modifier.weight(1f) else Modifier.width(40.dp))
                                .heightIn(min = 40.dp).semantics { contentDescription = "${item.name}: ${status.name}" },
                            shape = RoundedCornerShape(12.dp),
                            color = color.copy(alpha = if (isSelected) 0.18f else 0.06f),
                            contentColor = color,
                            border = BorderStroke(if (isSelected) 2.dp else 1.dp, color.copy(alpha = if (isSelected) 1f else 0.4f)),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                            ) {
                                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                                if (isSelected) {
                                    Text(status.name.lowercase().replaceFirstChar { it.titlecase() },
                                        modifier = Modifier.clearAndSetSemantics { },
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddItemField(viewModel: ChecklistViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = viewModel.newItemName,
            onValueChange = viewModel::updateNewItemName,
            label = { Text("Nuevo artículo") },
            singleLine = true,
            isError = viewModel.addError != null,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.addItem() }),
        )
        viewModel.addError?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        TextButton(
            onClick = viewModel::addItem,
            enabled = !viewModel.isAdding && viewModel.newItemName.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(if (viewModel.isAdding) "Agregando…" else "Agregar artículo") }
    }
}
