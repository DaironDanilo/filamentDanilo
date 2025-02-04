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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danilo.filamentdanilo.presentation.screens.components.FilamentSurfaceComposeView
import com.danilo.filamentdanilo.utils.showToast
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel = koinViewModel()
) {
    val context = LocalContext.current

    val title = homeViewModel.titleState.collectAsStateWithLifecycle()
    val toastMessage = homeViewModel.toastMessageState.collectAsStateWithLifecycle()
    val cameraFocalLength = homeViewModel.cameraFocalLength.collectAsStateWithLifecycle()

    // Remember the last shown message
    var lastToastMessage by remember { mutableStateOf<String?>(null) }

    if (toastMessage.value != null && toastMessage.value != lastToastMessage) {
        context.showToast(toastMessage.value.toString())
        lastToastMessage = toastMessage.value  // Update last shown message
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        FilamentSurfaceComposeView(
            onTitleChange = { newTitle ->
                homeViewModel.setTitle(newTitle)
            },
            onToastMessageChange = { message ->
                homeViewModel.setToastMessage(message)
            },
            cameraFocalLength = cameraFocalLength.value
        )
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            if (title.value.isNotEmpty()) {
                ElevatedCard(
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 6.dp
                    ),
                    modifier = Modifier
                        .height(48.dp)
                ) {
                    Text(
                        title.value,
                        color = Color.Black,
                        fontSize = 24.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }

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
                        value = cameraFocalLength.value,
                        onValueChange = { homeViewModel.setCameraFocalLength(it) },
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.secondary,
                            activeTrackColor = MaterialTheme.colorScheme.secondary,
                            inactiveTrackColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                        steps = 40,
                        valueRange = 50f..90f
                    )
                    Text(
                        text = "Camera Focal Length: %.2f".format(cameraFocalLength.value),
                        fontSize = 24.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
