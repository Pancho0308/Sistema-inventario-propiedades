package com.inventario.propiedades.ui.photos

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.inventario.propiedades.data.PhotoStorage
import com.inventario.propiedades.data.local.ItemEntity
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PhotoImage(
    path: String,
    photos: PhotoStorage,
    maxSide: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val bitmap by produceState<Bitmap?>(null, path, maxSide) {
        value = withContext(Dispatchers.IO) {
            try { photos.load(path, maxSide) } catch (_: IOException) { null }
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) {
            Image(image.asImageBitmap(), contentDescription, Modifier.fillMaxSize(), contentScale = contentScale)
        } else {
            Text(if (maxSide <= 128) "Foto" else "Foto no disponible", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun PhotoViewer(
    item: ItemEntity,
    photos: PhotoStorage,
    onDismiss: () -> Unit,
    onReplace: () -> Unit,
    onDelete: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(item.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onDismiss) { Text("Cerrar") }
            }
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = onReplace) { Text("Reemplazar") }
                TextButton(onClick = onDelete) { Text("Eliminar foto", color = MaterialTheme.colorScheme.error) }
            }
        },
    ) { padding ->
        PhotoImage(item.photoPath!!, photos, 1600,
            Modifier.fillMaxSize().padding(padding).padding(12.dp), "Foto de ${item.name}")
    }
}
