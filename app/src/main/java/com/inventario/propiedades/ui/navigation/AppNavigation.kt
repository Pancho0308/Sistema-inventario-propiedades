package com.inventario.propiedades.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.inventario.propiedades.data.ItemRepository
import com.inventario.propiedades.data.PhotoStorage
import com.inventario.propiedades.ui.photos.CameraScreen
import com.inventario.propiedades.ui.photos.CameraViewModel
import com.inventario.propiedades.data.PropertyRepository
import com.inventario.propiedades.data.RoomRepository
import com.inventario.propiedades.data.TemplateRepository
import com.inventario.propiedades.ui.templates.TemplatesScreen
import com.inventario.propiedades.ui.templates.TemplatesViewModel
import com.inventario.propiedades.ui.checklist.ChecklistScreen
import com.inventario.propiedades.ui.checklist.ChecklistViewModel
import com.inventario.propiedades.ui.properties.PropertiesScreen
import com.inventario.propiedades.ui.properties.PropertyViewModel
import com.inventario.propiedades.ui.rooms.RoomsScreen
import com.inventario.propiedades.ui.rooms.RoomsViewModel
import kotlinx.serialization.Serializable

@Serializable
object PropertiesRoute

@Serializable
object TemplatesRoute

@Serializable
data class RoomsRoute(val propertyId: Long)

@Serializable
data class ChecklistRoute(val roomId: Long)

@Serializable
data class CameraRoute(val roomId: Long, val itemId: Long)

@Composable
fun AppNavigation(repository: PropertyRepository, roomRepository: RoomRepository, itemRepository: ItemRepository,
    photos: PhotoStorage, templateRepository: TemplateRepository) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = PropertiesRoute) {
        composable<PropertiesRoute> {
            val propertyViewModel: PropertyViewModel = viewModel(factory = viewModelFactory {
                initializer { PropertyViewModel(repository, templateRepository) }
            })
            PropertiesScreen(
                viewModel = propertyViewModel,
                onPropertyClick = { propertyId ->
                    navController.navigate(RoomsRoute(propertyId)) { launchSingleTop = true }
                },
                onTemplates = { navController.navigate(TemplatesRoute) { launchSingleTop = true } },
            )
        }
        composable<RoomsRoute> { entry ->
            val route = entry.toRoute<RoomsRoute>()
            val roomsViewModel: RoomsViewModel = viewModel(factory = viewModelFactory {
                initializer { RoomsViewModel(roomRepository, route.propertyId) }
            })
            RoomsScreen(
                viewModel = roomsViewModel,
                onBack = { navController.popBackStack() },
                onRoomClick = { roomId ->
                    navController.navigate(ChecklistRoute(roomId)) { launchSingleTop = true }
                },
            )
        }
        composable<ChecklistRoute> { entry ->
            val route = entry.toRoute<ChecklistRoute>()
            val checklistViewModel: ChecklistViewModel = viewModel(factory = viewModelFactory {
                initializer { ChecklistViewModel(itemRepository, route.roomId) }
            })
            ChecklistScreen(checklistViewModel, photos,
                onCamera = { itemId -> navController.navigate(CameraRoute(route.roomId, itemId)) { launchSingleTop = true } },
                onBack = { navController.popBackStack() })
        }
        composable<CameraRoute> { entry ->
            val route = entry.toRoute<CameraRoute>()
            val cameraViewModel: CameraViewModel = viewModel(factory = viewModelFactory {
                initializer { CameraViewModel(itemRepository, photos, route.roomId, route.itemId) }
            })
            CameraScreen(cameraViewModel,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() })
        }
        composable<TemplatesRoute> {
            val templatesViewModel: TemplatesViewModel = viewModel(factory = viewModelFactory {
                initializer { TemplatesViewModel(templateRepository) }
            })
            TemplatesScreen(templatesViewModel, onBack = { navController.popBackStack() })
        }
    }
}
