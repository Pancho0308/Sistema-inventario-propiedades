package com.inventario.propiedades

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.inventario.propiedades.data.PropertyRepository
import com.inventario.propiedades.data.local.InventoryDatabase
import com.inventario.propiedades.ui.navigation.AppNavigation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = PropertyRepository(InventoryDatabase.getInstance(applicationContext).propertyDao())
        setContent {
            MaterialTheme {
                AppNavigation(repository)
            }
        }
    }
}
