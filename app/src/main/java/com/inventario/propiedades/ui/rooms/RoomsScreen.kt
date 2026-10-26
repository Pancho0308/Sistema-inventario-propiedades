package com.inventario.propiedades.ui.rooms

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomsScreen() {
    Scaffold(topBar = { TopAppBar(title = { Text("Habitaciones") }) }) { padding ->
        // El contenido de habitaciones corresponde a la Fase 3.
        Box(Modifier.fillMaxSize().padding(padding))
    }
}
