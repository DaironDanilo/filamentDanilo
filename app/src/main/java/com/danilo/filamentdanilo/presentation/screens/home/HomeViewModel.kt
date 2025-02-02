package com.danilo.filamentdanilo.presentation.screens.home

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class HomeViewModel : ViewModel() {
    private val _cameraFocalLength = MutableStateFlow(DEFAULT_FOCAL_LENGTH)
    val cameraFocalLength: StateFlow<Float> = _cameraFocalLength

    fun setCameraFocalLength(focalLength: Float) {
        _cameraFocalLength.value = focalLength
    }

    companion object {
        const val DEFAULT_FOCAL_LENGTH = 90f
    }
}
