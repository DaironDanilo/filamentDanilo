package com.danilo.filamentdanilo.presentation.screens.components

import android.view.SurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.danilo.filamentdanilo.presentation.rendering.ModelRenderer

@Composable
fun FilamentSurfaceComposeView(
    titleState: MutableState<String>,
    cameraFocalLength: Float,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Adds view to Compose
    AndroidView(
        modifier = Modifier.fillMaxSize(), // Occupy the max size in the Compose UI tree
        factory = { context ->
            SurfaceView(context).apply {
                val modelRenderer = ModelRenderer(surfaceView = this)
                modelRenderer.initSurfaceView(titleState, lifecycle)
                this.tag = modelRenderer
            }
        },
        update = { surfaceView ->
            val modelRenderer = surfaceView.tag as? ModelRenderer
            modelRenderer?.updateCameraFocalLength(cameraFocalLength)
        }
    )
}
