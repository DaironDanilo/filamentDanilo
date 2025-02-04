package com.danilo.filamentdanilo.presentation.interaction

import android.view.GestureDetector
import android.view.MotionEvent
import com.google.android.filament.utils.AutomationEngine
import com.google.android.filament.utils.ModelViewer

// Just for testing purposes, this releases the current model and reloads the default model.
class SceneDoubleTapListener(
    private val modelViewer: ModelViewer,
    private val automation: AutomationEngine,
    private val createRenderables: (ModelViewer, AutomationEngine) -> Unit,
    private val onTitleChange: (String) -> Unit,
) : GestureDetector.SimpleOnGestureListener() {
    override fun onDoubleTap(e: MotionEvent): Boolean {
        modelViewer.destroyModel()
        createRenderables(modelViewer, automation)
        onTitleChange("https://google.github.io/filament/remote")
        return super.onDoubleTap(e)
    }
}
