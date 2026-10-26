package com.inventario.propiedades

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.lifecycleScope
import com.inventario.propiedades.data.local.InventoryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch(Dispatchers.IO) {
            // Abre la base y carga la muestra sin bloquear el hilo principal.
            InventoryDatabase.getInstance(applicationContext).openHelper.writableDatabase
        }
        setContent {
            MaterialTheme {
                // La interfaz se implementará en una fase posterior.
                Surface { }
            }
        }
    }
}
