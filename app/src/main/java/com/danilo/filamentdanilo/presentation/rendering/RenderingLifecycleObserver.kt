package com.danilo.filamentdanilo.presentation.rendering

import android.view.Choreographer
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.filament.utils.RemoteServer

class MyLifecycleTracker(
    private val choreographer: Choreographer,
    private val frameScheduler: Choreographer.FrameCallback,
    private val remoteServer: RemoteServer?
) : DefaultLifecycleObserver {
    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        choreographer.postFrameCallback(frameScheduler)
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        choreographer.removeFrameCallback(frameScheduler)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        choreographer.removeFrameCallback(frameScheduler)
        remoteServer?.close()
    }
}
