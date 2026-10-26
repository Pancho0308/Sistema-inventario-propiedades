package com.inventario.propiedades.ui.photos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(viewModel: CameraViewModel, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val preferences = remember(context) { context.getSharedPreferences("camera_permission", 0) }
    fun hasCameraPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var hasPermission by remember { mutableStateOf(hasCameraPermission()) }
    var isReady by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    val executor = remember(context) { ContextCompat.getMainExecutor(context) }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    val previewView = remember(context) {
        PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission && !preferences.getBoolean("requested", false)) {
            preferences.edit().putBoolean("requested", true).apply()
            launcher.launch(Manifest.permission.CAMERA)
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasPermission = hasCameraPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(hasPermission, lifecycleOwner, previewView) {
        var provider: ProcessCameraProvider? = null
        var disposed = false
        val preview = Preview.Builder().build()
        isReady = false
        cameraError = null
        if (hasPermission) {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if (!disposed) {
                    try {
                        val cameraProvider = future.get()
                        provider = cameraProvider
                        val selector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        } else CameraSelector.DEFAULT_FRONT_CAMERA
                        preview.setSurfaceProvider(previewView.surfaceProvider)
                        cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
                        isReady = true
                    } catch (_: Exception) {
                        cameraError = "No se pudo abrir la cámara. Comprueba el permiso o vuelve a intentarlo."
                    }
                }
            }, executor)
        }
        onDispose {
            disposed = true
            provider?.unbind(preview, imageCapture)
        }
    }
    LaunchedEffect(viewModel.isSaved) { if (viewModel.isSaved) onSaved() }
    BackHandler(enabled = viewModel.isSaving) { }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Foto del artículo") }, navigationIcon = {
                TextButton(onClick = onBack, enabled = !viewModel.isSaving) { Text("Volver") }
            })
        },
        bottomBar = {
            if (hasPermission) {
                Button(
                    onClick = {
                        imageCapture.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
                        viewModel.capture(imageCapture, executor)
                    },
                    enabled = isReady && !viewModel.isSaving && !viewModel.isSaved,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                ) { Text(if (viewModel.isSaving) "Guardando…" else "Capturar") }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            if (!hasPermission) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Necesitamos permiso de cámara para fotografiar los artículos. Las fotos se guardan solo en este dispositivo.")
                    Text("Si rechazaste el permiso, puedes habilitarlo en Ajustes > Permisos > Cámara.")
                    Button(onClick = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                    }) { Text("Abrir ajustes") }
                }
            } else {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                if (!isReady && cameraError == null || viewModel.isSaving) {
                    CircularProgressIndicator(Modifier.semantics { contentDescription = "Preparando o guardando la foto" })
                }
                val error = viewModel.error ?: cameraError
                if (error != null) {
                    androidx.compose.material3.Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                        Text(error, color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
            }
        }
    }
}
