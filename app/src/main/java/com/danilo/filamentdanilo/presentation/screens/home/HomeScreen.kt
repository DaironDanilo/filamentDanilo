package com.danilo.filamentdanilo.presentation.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danilo.filamentdanilo.presentation.screens.components.FilamentSurfaceComposeView

@Composable
fun HomeScreen() {
    val titleState = remember { mutableStateOf("https://google.github.io/filament/remote") }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        FilamentSurfaceComposeView(titleState)
        Column(Modifier
            .align(Alignment.TopCenter)
            .windowInsetsPadding(WindowInsets.statusBars)) {
            if (titleState.value.isNotEmpty()) {
                ElevatedCard(
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 6.dp
                    ),
                    modifier = Modifier
                        .height(48.dp)
                ) {
                    Text(
                        titleState.value,
                        color = Color.Black,
                        fontSize = 24.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
        var sliderPosition by remember { mutableFloatStateOf(0f) }
//             TODO("update this to use slider later")
//            modelViewer.cameraFocalLength = sliderPosition
//            updateRootTransform()
        Column(modifier = Modifier.align(Alignment.BottomCenter)) {
            ElevatedCard(
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 6.dp
                ),
                modifier = Modifier
            ) {
                Column(
                    Modifier
                        .fillMaxWidth(0.9f)
                        .padding(16.dp)
                ) {
                    Slider(
                        value = sliderPosition,
                        onValueChange = { sliderPosition = it },
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.secondary,
                            activeTrackColor = MaterialTheme.colorScheme.secondary,
                            inactiveTrackColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                        steps = 40,
                        valueRange = 50f..90f
                    )
                    Text(text = "Camera Focal Length: $sliderPosition", fontSize = 24.sp)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
