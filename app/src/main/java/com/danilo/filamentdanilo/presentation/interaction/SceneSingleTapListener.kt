package com.danilo.filamentdanilo.presentation.interaction

import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.SurfaceView
import com.google.android.filament.utils.ModelViewer

// Just for testing purposes
class SceneSingleTapListener(
    private val modelViewer: ModelViewer,
    private val surfaceView: SurfaceView
) : GestureDetector.SimpleOnGestureListener() {
    override fun onSingleTapUp(event: MotionEvent): Boolean {
        modelViewer.view.pick(
            event.x.toInt(),
            surfaceView.height - event.y.toInt(),
            surfaceView.handler,
            {
                val name = modelViewer.asset!!.getName(it.renderable)
                Log.v("Filament", "Picked ${it.renderable}: " + name)
            },
        )
        return super.onSingleTapUp(event)
    }
}
