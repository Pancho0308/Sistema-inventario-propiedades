package com.inventario.propiedades

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inventario.propiedades.data.ItemRepository
import com.inventario.propiedades.data.PropertyRepository
import com.inventario.propiedades.data.RoomRepository
import com.inventario.propiedades.data.TemplateRepository
import com.inventario.propiedades.ui.navigation.AppNavigation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as InventoryApplication
        val database = app.database
        val photos = app.photos
        val repository = PropertyRepository(database, photos)
        val roomRepository = RoomRepository(database, photos)
        val itemRepository = ItemRepository(database, photos)
        val templateRepository = TemplateRepository(database)
        setContent {
            MaterialTheme {
                val startup by app.startup.collectAsStateWithLifecycle()
                if (startup.isReady) {
                    AppNavigation(repository, roomRepository, itemRepository, photos, templateRepository)
                } else {
                    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            val error = startup.error
                            if (error == null) CircularProgressIndicator()
                            else {
                                Text(error, color = MaterialTheme.colorScheme.error)
                                Button(onClick = app::initialize) { Text("Reintentar") }
                            }
                        }
                    }
                }
            }
        }
    }
}
