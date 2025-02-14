package com.danilo.filamentdanilo.presentation.rendering

import android.annotation.SuppressLint
import android.content.Context
import android.view.Choreographer
import android.view.GestureDetector
import android.view.SurfaceView
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

class ModelRenderer(
    private val surfaceView: SurfaceView,
    private val modelViewer: ModelViewer = ModelViewer(surfaceView),
    private val automationEngine: AutomationEngine = AutomationEngine(),
    private val remoteServer: RemoteServer = RemoteServer(REMOTE_PORT),
    private val viewerContent: ViewerContent = ViewerContent(),
    private val choreographer: Choreographer = Choreographer.getInstance(),
) {

    private val loadStartTime = 0L
    fun initSurfaceView(
        onTitleChange: (String) -> Unit,
        onToastMessageChange: (String) -> Unit,
        lifecycle: Lifecycle,
    ) {
        val context = surfaceView.context

        onToastMessageChange("To load a new model, go to the above URL on your host machine.")

        val frameScheduler = FrameCallback(
            choreographer = choreographer,
            modelViewer = modelViewer,
            automation = automationEngine,
            viewerContent = viewerContent,
            remoteServer = remoteServer,
            loadStartTime = loadStartTime,
            onToastMessageChange = onToastMessageChange,
            onTitleChange = onTitleChange,
            context = context,
            updateRootTransform = { mv, aut -> updateRootTransform(mv, aut) }
        )

        viewerContent.apply {
            view = modelViewer.view
            sunlight = modelViewer.light
            lightManager = modelViewer.engine.lightManager
            scene = modelViewer.scene
            renderer = modelViewer.renderer
        }

        setupGestureListeners(context, onTitleChange)
        createDefaultRenderables(modelViewer, automationEngine, context)
        createIndirectLight(modelViewer, viewerContent, context)

        lifecycle.addObserver(
            RenderingLifecycleTracker(
                choreographer,
                frameScheduler,
                remoteServer
            )
        )
        configureViewSettings(modelViewer.view)
    }

    fun updateCameraFocalLength(focalLength: Float) {
        modelViewer.cameraFocalLength = focalLength
        updateRootTransform(modelViewer, automationEngine)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestureListeners(
        context: Context,
        onTitleChange: (String) -> Unit,
    ) {
        val doubleTapListener = SceneDoubleTapListener(
            modelViewer = modelViewer,
            automation = automationEngine,
            createRenderables = { mv, aut ->
                createDefaultRenderables(mv, aut, context)
            },
            onTitleChange = onTitleChange,
        )
        val singleTapListener = SceneSingleTapListener(
            modelViewer = modelViewer,
            surfaceView = surfaceView
        )

        val doubleTapDetector = GestureDetector(context, doubleTapListener)
        val singleTapDetector = GestureDetector(context, singleTapListener)

        surfaceView.setOnTouchListener { _, event ->
            modelViewer.onTouchEvent(event)
            doubleTapDetector.onTouchEvent(event)
            singleTapDetector.onTouchEvent(event)
            true
        }
    }

    private fun updateRootTransform(modelViewer: ModelViewer, automation: AutomationEngine) {
        if (automation.viewerOptions.autoScaleEnabled) {
            modelViewer.transformToUnitCube()
        } else {
            modelViewer.clearRootTransform()
        }
    }

    private fun createDefaultRenderables(
        modelViewer: ModelViewer,
        automation: AutomationEngine,
        context: Context
    ) {
        val buffer = context.assets.open(DEFAULT_MODEL).use { input ->
            val bytes = ByteArray(input.available())
            input.read(bytes)
            ByteBuffer.wrap(bytes)
        }

        modelViewer.loadModelGltfAsync(buffer) { uri ->
            readCompressedAsset(
                "models/$uri",
                context
            )
        }
        updateRootTransform(modelViewer, automation)
    }

    private fun createIndirectLight(
        modelViewer: ModelViewer,
        viewerContent: ViewerContent,
        context: Context
    ) {
        val engine = modelViewer.engine
        val scene = modelViewer.scene
        readCompressedAsset("$DEFAULT_ENV/default_env_ibl.ktx", context).let {
            scene.indirectLight = KTX1Loader.createIndirectLight(engine, it)
            scene.indirectLight!!.intensity = 30_000.0f
            viewerContent.indirectLight = modelViewer.scene.indirectLight
        }
        readCompressedAsset("$DEFAULT_ENV/default_env_skybox.ktx", context).let {
            scene.skybox = KTX1Loader.createSkybox(engine, it)
        }
    }

    private fun readCompressedAsset(assetName: String, context: Context): ByteBuffer {
        val input = context.assets.open(assetName)
        val bytes = ByteArray(input.available())
        input.read(bytes)
        return ByteBuffer.wrap(bytes)
    }

    private fun configureViewSettings(view: View) {
        /*
         * Note: The settings below are overridden when connecting to the remote UI.
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

    companion object {
        const val DEFAULT_ENV = "envs/default_env"
        const val DEFAULT_MODEL = "models/scene.gltf"
        const val REMOTE_PORT = 8082
    }
}
