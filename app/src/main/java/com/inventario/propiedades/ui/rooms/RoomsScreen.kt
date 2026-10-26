package com.inventario.propiedades.ui.rooms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomsScreen(viewModel: RoomsViewModel, onBack: () -> Unit, onRoomClick: (Long) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val error = state.error
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Habitaciones") },
                navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("Volver") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::openCreateDialog,
                modifier = Modifier.size(56.dp).semantics { contentDescription = "Crear habitación" },
            ) {
                Text("+", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.clearAndSetSemantics { })
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.semantics { contentDescription = "Cargando habitaciones" })
            }
            error != null -> Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(padding).padding(24.dp))
            state.rooms.isEmpty() -> Text("Toca + para crear una habitación.", modifier = Modifier.padding(padding).padding(24.dp))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.rooms, key = { it.room.id }) { progress ->
                    Card(onClick = { onRoomClick(progress.room.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(progress.room.name, style = MaterialTheme.typography.titleMedium)
                            Text("${progress.reviewedItems}/${progress.totalItems} revisados")
                        }
                    }
                }
            }
        }
    }
    if (viewModel.form.isOpen) CreateRoomDialog(viewModel)
}

@Composable
private fun CreateRoomDialog(viewModel: RoomsViewModel) {
    val form = viewModel.form
    AlertDialog(
        onDismissRequest = viewModel::dismissCreateDialog,
        title = { Text("Nueva habitación") },
        text = {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = viewModel::updateName,
                    label = { Text("Nombre") },
                    singleLine = true,
                    enabled = !form.isSaving,
                    isError = form.error != null,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { viewModel.createRoom() }),
                )
                form.error?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = viewModel::createRoom,
                enabled = !form.isSaving && form.name.isNotBlank(),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(if (form.isSaving) "Guardando…" else "Crear") }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissCreateDialog, enabled = !form.isSaving, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancelar")
            }
        },
    )
}
