package com.inventario.propiedades

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.inventario.propiedades.data.PhotoStorage
import com.inventario.propiedades.data.ItemRepository
import com.inventario.propiedades.data.PropertyRepository
import com.inventario.propiedades.data.RoomRepository
import com.inventario.propiedades.data.local.InventoryDatabase
import com.inventario.propiedades.ui.navigation.AppNavigation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = InventoryDatabase.getInstance(applicationContext)
        val photos = PhotoStorage(filesDir)
        val repository = PropertyRepository(database, photos)
        val roomRepository = RoomRepository(database, photos)
        val itemRepository = ItemRepository(database, photos)
        setContent {
            MaterialTheme {
                AppNavigation(repository, roomRepository, itemRepository, photos)
            }
        }
    }
}
