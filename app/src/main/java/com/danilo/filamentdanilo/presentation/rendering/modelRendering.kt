package com.danilo.filamentdanilo.presentation.rendering

import android.annotation.SuppressLint
import android.view.Choreographer
import android.view.GestureDetector
import android.view.SurfaceView
import android.widget.Toast
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.Lifecycle
import com.danilo.filamentdanilo.presentation.interaction.SceneDoubleTapListener
import com.danilo.filamentdanilo.presentation.interaction.SceneSingleTapListener
import com.google.android.filament.View
import com.google.android.filament.utils.AutomationEngine
import com.google.android.filament.utils.AutomationEngine.ViewerContent
import com.google.android.filament.utils.KTX1Loader
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.RemoteServer
import java.nio.ByteBuffer

class ModelRenderer {

    @SuppressLint("ClickableViewAccessibility")
    fun initSurfaceView(
        surfaceView: SurfaceView,
        titleState: MutableState<String>,
        lifecycle: Lifecycle,
    ) {
        val loadStartTime = 0L
        val statusToast = mutableStateOf<Toast?>(null)
        val statusText = mutableStateOf<String?>(null)
        val remoteServer = RemoteServer(8082)
        val choreographer = Choreographer.getInstance()
        val modelViewer = ModelViewer(surfaceView)
        val viewerContent = ViewerContent()
        val automation = AutomationEngine()
        val context = surfaceView.context

        //        setStatusText(
//            text = "To load a new model, go to the above URL on your host machine.",
//            statusToast = statusToast,
//            statusText = statusText,
//        )
        fun updateRootTransform(modelViewer: ModelViewer, automation: AutomationEngine) {
            if (automation.viewerOptions.autoScaleEnabled) {
                modelViewer.transformToUnitCube()
            } else {
                modelViewer.clearRootTransform()
            }
        }

        val frameScheduler = FrameCallback(
            choreographer = choreographer,
            modelViewer = modelViewer,
            automation = automation,
            viewerContent = viewerContent,
            remoteServer = remoteServer,
            loadStartTime = loadStartTime,
            statusToast = statusToast,
            statusText = statusText,
            titleState = titleState,
            context = context,
            updateRootTransform = { mv, aut -> updateRootTransform(mv, aut) }
        )

        fun readCompressedAsset(assetName: String): ByteBuffer {
            val input = context.assets.open(assetName)
            val bytes = ByteArray(input.available())
            input.read(bytes)
            return ByteBuffer.wrap(bytes)
        }

        fun createDefaultRenderables(modelViewer: ModelViewer, automation: AutomationEngine) {
            val buffer = context.assets.open("models/scene.gltf").use { input ->
                val bytes = ByteArray(input.available())
                input.read(bytes)
                ByteBuffer.wrap(bytes)
            }

            modelViewer.loadModelGltfAsync(buffer) { uri -> readCompressedAsset("models/$uri") }
            updateRootTransform(modelViewer, automation)
        }

        fun createIndirectLight(modelViewer: ModelViewer, viewerContent: ViewerContent) {
            val engine = modelViewer.engine
            val scene = modelViewer.scene
            val ibl = "envs/default_env"
            readCompressedAsset("$ibl/default_env_ibl.ktx").let {
                scene.indirectLight = KTX1Loader.createIndirectLight(engine, it)
                scene.indirectLight!!.intensity = 30_000.0f
                viewerContent.indirectLight = modelViewer.scene.indirectLight
            }
            readCompressedAsset("$ibl/default_env_skybox.ktx").let {
                scene.skybox = KTX1Loader.createSkybox(engine, it)
            }
        }

        val doubleTapListener = SceneDoubleTapListener(
            modelViewer = modelViewer,
            automation = automation,
            createRenderables = { mv, aut ->
                createDefaultRenderables(mv, aut)
            }
        )
        val singleTapListener = SceneSingleTapListener(
            modelViewer = modelViewer,
            surfaceView = surfaceView
        )

        val doubleTapDetector = GestureDetector(context, doubleTapListener)
        val singleTapDetector = GestureDetector(context, singleTapListener)

        viewerContent.view = modelViewer.view
        viewerContent.sunlight = modelViewer.light
        viewerContent.lightManager = modelViewer.engine.lightManager
        viewerContent.scene = modelViewer.scene
        viewerContent.renderer = modelViewer.renderer

        surfaceView.setOnTouchListener { _, event ->
            modelViewer.onTouchEvent(event)
            doubleTapDetector.onTouchEvent(event)
            singleTapDetector.onTouchEvent(event)
            true
        }

        createDefaultRenderables(modelViewer, automation)
        createIndirectLight(modelViewer, viewerContent)

        val view = modelViewer.view
        lifecycle.addObserver(MyLifecycleTracker(choreographer, frameScheduler, remoteServer))
        /*
     * Note: The settings below are overriden when connecting to the remote UI.
     */

        // on mobile, better use lower quality color buffer
        view.renderQuality = view.renderQuality.apply {
            hdrColorBuffer = View.QualityLevel.MEDIUM
        }

        // dynamic resolution often helps a lot
        view.dynamicResolutionOptions = view.dynamicResolutionOptions.apply {
            enabled = true
            quality = View.QualityLevel.MEDIUM
        }

        // MSAA is needed with dynamic resolution MEDIUM
        view.multiSampleAntiAliasingOptions = view.multiSampleAntiAliasingOptions.apply {
            enabled = true
        }

        // FXAA is pretty cheap and helps a lot
        view.antiAliasing = View.AntiAliasing.FXAA

        // ambient occlusion is the cheapest effect that adds a lot of quality
        view.ambientOcclusionOptions = view.ambientOcclusionOptions.apply {
            enabled = true
        }

        // bloom is pretty expensive but adds a fair amount of realism
        view.bloomOptions = view.bloomOptions.apply {
            enabled = true
        }
    }
}
