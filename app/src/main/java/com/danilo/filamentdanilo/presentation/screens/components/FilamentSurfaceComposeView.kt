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
fun FilamentSurfaceComposeView(titleState: MutableState<String>) {
    // Adds view to Compose
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    AndroidView(
        modifier = Modifier.fillMaxSize(), // Occupy the max size in the Compose UI tree
        factory = { context ->
            val modelRenderer = ModelRenderer()
            SurfaceView(context).apply {
                modelRenderer.initSurfaceView(this, titleState, lifecycle)
            }
        },
        update = { _ ->
            // no-op
        }
    )
}
