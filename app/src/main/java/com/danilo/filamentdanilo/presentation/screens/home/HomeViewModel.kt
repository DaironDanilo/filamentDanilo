package com.danilo.filamentdanilo.presentation.screens.home

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class HomeViewModel : ViewModel() {
    private val _cameraFocalLength = MutableStateFlow(DEFAULT_FOCAL_LENGTH)
    val cameraFocalLength: StateFlow<Float> = _cameraFocalLength

    private val title = MutableStateFlow("https://google.github.io/filament/remote")
    val titleState: StateFlow<String> = title

    private val toastMessage: MutableStateFlow<String?> = MutableStateFlow(null)
    val toastMessageState: StateFlow<String?> = toastMessage

    fun setCameraFocalLength(focalLength: Float) {
        _cameraFocalLength.value = focalLength
    }

    fun setTitle(newTitle: String) {
        title.value = newTitle
    }

    fun setToastMessage(message: String) {
        toastMessage.value = message
    }

    companion object {
        const val DEFAULT_FOCAL_LENGTH = 90f
    }
}
