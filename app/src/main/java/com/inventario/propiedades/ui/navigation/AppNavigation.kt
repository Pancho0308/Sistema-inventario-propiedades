package com.inventario.propiedades.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.inventario.propiedades.data.PropertyRepository
import com.inventario.propiedades.ui.properties.PropertiesScreen
import com.inventario.propiedades.ui.properties.PropertyViewModel
import com.inventario.propiedades.ui.rooms.RoomsScreen
import kotlinx.serialization.Serializable

@Serializable
object PropertiesRoute

@Serializable
data class RoomsRoute(val propertyId: Long)

@Composable
fun AppNavigation(repository: PropertyRepository) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = PropertiesRoute) {
        composable<PropertiesRoute> {
            val propertyViewModel: PropertyViewModel = viewModel(factory = viewModelFactory {
                initializer { PropertyViewModel(repository) }
            })
            PropertiesScreen(
                viewModel = propertyViewModel,
                onPropertyClick = { propertyId ->
                    navController.navigate(RoomsRoute(propertyId)) { launchSingleTop = true }
                },
            )
        }
        composable<RoomsRoute> {
            RoomsScreen()
        }
    }
}
