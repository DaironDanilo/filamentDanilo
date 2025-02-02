package com.danilo.filamentdanilo

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.MutableState
import com.danilo.filamentdanilo.presentation.screens.home.HomeScreen
import com.google.android.filament.utils.Utils

class MainActivity : ComponentActivity() {

    companion object {
        // Load the library for the utility layer, which in turn loads gltfio and the Filament core.
        init {
            Utils.init()
        }

        const val TAG = "gltf-viewer"
    }

    private val onBackPressedCallback: OnBackPressedCallback =
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent {
            HomeScreen()
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    // TODO move this function to a common place
    private fun setStatusText(
        text: String,
        statusToast: MutableState<Toast?>,
        statusText: MutableState<String?>,
    ) {
        runOnUiThread {
            if (statusToast.value == null || statusText.value != text) {
                statusText.value = text
                statusToast.value = Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT)
                statusToast.value?.show()
            }
        }
    }
}
