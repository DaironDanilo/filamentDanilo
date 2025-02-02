package com.danilo.filamentdanilo

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.Choreographer
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.filament.Fence
import com.google.android.filament.IndirectLight
import com.google.android.filament.Material
import com.google.android.filament.Skybox
import com.google.android.filament.View
import com.google.android.filament.utils.AutomationEngine
import com.google.android.filament.utils.AutomationEngine.ViewerContent
import com.google.android.filament.utils.HDRLoader
import com.google.android.filament.utils.IBLPrefilterContext
import com.google.android.filament.utils.KTX1Loader
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.RemoteServer
import com.google.android.filament.utils.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.net.URI
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

class MainActivity : ComponentActivity() {

    companion object {
        // Load the library for the utility layer, which in turn loads gltfio and the Filament core.
        init {
            Utils.init()
        }

        private const val TAG = "gltf-viewer"
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent {
            HomeScreen()
        }
    }

    @Composable
    fun HomeScreen() {
        val titleState = remember { mutableStateOf("https://google.github.io/filament/remote") }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            FilamentSurfaceComposeView(titleState)
            Column(Modifier.align(Alignment.TopCenter)) {
                Spacer(modifier = Modifier.height(16.dp))
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

    @Composable
    fun FilamentSurfaceComposeView(titleState: MutableState<String>) {
        // Adds view to Compose
        AndroidView(
            modifier = Modifier.fillMaxSize(), // Occupy the max size in the Compose UI tree
            factory = { context ->
                SurfaceView(context).apply {
                    initSurfaceView(this, titleState)
                }
            },
            update = { _ ->
                // no-op
            }
        )
    }

    private fun initSurfaceView(
        surfaceView: SurfaceView,
        titleState: MutableState<String>,
    ) {
        val loadStartTime = 0L
        val statusToast = mutableStateOf<Toast?>(null)
        val statusText = mutableStateOf<String?>(null)
        val remoteServer = RemoteServer(8082)
        val choreographer = Choreographer.getInstance()
        val modelViewer = ModelViewer(surfaceView)
        val viewerContent = ViewerContent()
        val automation = AutomationEngine()
        setStatusText(
            text = "To load a new model, go to the above URL on your host machine.",
            statusToast = statusToast,
            statusText = statusText,
        )
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
        )
        val doubleTapListener = DoubleTapListener(modelViewer, automation)
        val singleTapListener =
            SingleTapListener(modelViewer = modelViewer, surfaceView = surfaceView)

        val doubleTapDetector = GestureDetector(applicationContext, doubleTapListener)
        val singleTapDetector = GestureDetector(applicationContext, singleTapListener)

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

    private fun createDefaultRenderables(modelViewer: ModelViewer, automation: AutomationEngine) {
        val buffer = assets.open("models/scene.gltf").use { input ->
            val bytes = ByteArray(input.available())
            input.read(bytes)
            ByteBuffer.wrap(bytes)
        }

        modelViewer.loadModelGltfAsync(buffer) { uri -> readCompressedAsset("models/$uri") }
        updateRootTransform(modelViewer, automation)
    }

    private fun createIndirectLight(modelViewer: ModelViewer, viewerContent: ViewerContent) {
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

    private fun readCompressedAsset(assetName: String): ByteBuffer {
        val input = assets.open(assetName)
        val bytes = ByteArray(input.available())
        input.read(bytes)
        return ByteBuffer.wrap(bytes)
    }

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

    private suspend fun loadGlb(
        message: RemoteServer.ReceivedMessage,
        modelViewer: ModelViewer,
        automation: AutomationEngine,
        onLoadStartFenceChange: (Fence) -> Unit,
        onLoadStartTimeChange: (Long) -> Unit,
    ) {
        withContext(Dispatchers.Main) {
            modelViewer.destroyModel()
            modelViewer.loadModelGlb(message.buffer)
            updateRootTransform(modelViewer, automation)
            onLoadStartTimeChange(System.nanoTime())
            onLoadStartFenceChange(modelViewer.engine.createFence())
        }
    }

    private suspend fun loadHdr(
        message: RemoteServer.ReceivedMessage,
        modelViewer: ModelViewer,
        viewerContent: ViewerContent,
        statusToast: MutableState<Toast?>,
        statusText: MutableState<String?>,
    ) {
        withContext(Dispatchers.Main) {
            val engine = modelViewer.engine
            val equirect = HDRLoader.createTexture(engine, message.buffer)
            if (equirect == null) {
                setStatusText("Could not decode HDR file.", statusToast, statusText)
            } else {
                setStatusText("Successfully decoded HDR file.", statusToast, statusText)

                val context = IBLPrefilterContext(engine)
                val equirectToCubemap = IBLPrefilterContext.EquirectangularToCubemap(context)
                val skyboxTexture = equirectToCubemap.run(equirect)!!
                engine.destroyTexture(equirect)

                val specularFilter = IBLPrefilterContext.SpecularFilter(context)
                val reflections = specularFilter.run(skyboxTexture)

                val ibl = IndirectLight.Builder()
                    .reflections(reflections)
                    .intensity(30000.0f)
                    .build(engine)

                val sky = Skybox.Builder().environment(skyboxTexture).build(engine)

                specularFilter.destroy()
                equirectToCubemap.destroy()
                context.destroy()

                // destroy the previous IBl
                engine.destroyIndirectLight(modelViewer.scene.indirectLight!!)
                engine.destroySkybox(modelViewer.scene.skybox!!)

                modelViewer.scene.skybox = sky
                modelViewer.scene.indirectLight = ibl
                viewerContent.indirectLight = ibl

            }
        }
    }

    private suspend fun loadZip(
        message: RemoteServer.ReceivedMessage,
        modelViewer: ModelViewer,
        automation: AutomationEngine,
        statusToast: MutableState<Toast?>,
        statusText: MutableState<String?>,
        onLoadStartFenceChange: (Fence) -> Unit,
        onLoadStartTimeChange: (Long) -> Unit,
    ) {
        // To alleviate memory pressure, remove the old model before deflating the zip.
        withContext(Dispatchers.Main) {
            modelViewer.destroyModel()
        }

        // Large zip files should first be written to a file to prevent OOM.
        // It is also crucial that we null out the message "buffer" field.
        val (zipStream, zipFile) = withContext(Dispatchers.IO) {
            val file = File.createTempFile("incoming", "zip", cacheDir)
            val raf = RandomAccessFile(file, "rw")
            raf.channel.write(message.buffer)
            message.buffer = null
            raf.seek(0)
            Pair(FileInputStream(file), file)
        }

        // Deflate each resource using the IO dispatcher, one by one.
        var gltfPath: String? = null
        var outOfMemory: String? = null
        val pathToBufferMapping = withContext(Dispatchers.IO) {
            val deflater = ZipInputStream(zipStream)
            val mapping = HashMap<String, Buffer>()
            while (true) {
                val entry = deflater.nextEntry ?: break
                if (entry.isDirectory) continue

                // This isn't strictly required, but as an optimization
                // we ignore common junk that often pollutes ZIP files.
                if (entry.name.startsWith("__MACOSX")) continue
                if (entry.name.startsWith(".DS_Store")) continue

                val uri = entry.name
                val byteArray: ByteArray? = try {
                    deflater.readBytes()
                } catch (e: OutOfMemoryError) {
                    outOfMemory = uri
                    break
                }
                Log.i(TAG, "Deflated ${byteArray!!.size} bytes from $uri")
                val buffer = ByteBuffer.wrap(byteArray)
                mapping[uri] = buffer
                if (uri.endsWith(".gltf") || uri.endsWith(".glb")) {
                    gltfPath = uri
                }
            }
            mapping
        }

        zipFile.delete()

        if (gltfPath == null) {
            setStatusText(
                "Could not find .gltf or .glb in the zip.",
                statusToast,
                statusText,
            )
            return
        }

        if (outOfMemory != null) {
            setStatusText(
                "Out of memory while deflating $outOfMemory",
                statusToast,
                statusText,
            )
            return
        }

        val gltfBuffer = pathToBufferMapping[gltfPath]!!

        // In a zip file, the gltf file might be in the same folder as resources, or in a different
        // folder. It is crucial to test against both of these cases. In any case, the resource
        // paths are all specified relative to the location of the gltf file.
        var prefix = URI(gltfPath!!).resolve(".")

        withContext(Dispatchers.Main) {
            if (gltfPath!!.endsWith(".glb")) {
                modelViewer.loadModelGlb(gltfBuffer)
            } else {
                modelViewer.loadModelGltf(gltfBuffer) { uri ->
                    val path = prefix.resolve(uri).toString()
                    if (!pathToBufferMapping.contains(path)) {
                        Log.e(
                            TAG,
                            "Could not find '$uri' in zip using prefix '$prefix' and base path '${gltfPath!!}'"
                        )
                        setStatusText(
                            "Zip is missing $path",
                            statusToast,
                            statusText,
                        )
                    }
                    pathToBufferMapping[path]
                }
            }
            updateRootTransform(modelViewer, automation)
            onLoadStartTimeChange(System.nanoTime())
            onLoadStartFenceChange(modelViewer.engine.createFence())
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    fun loadModelData(
        message: RemoteServer.ReceivedMessage,
        modelViewer: ModelViewer,
        viewerContent: ViewerContent,
        automation: AutomationEngine,
        statusToast: MutableState<Toast?>,
        statusText: MutableState<String?>,
        titleState: MutableState<String>,
        onLoadStartFenceChange: (Fence) -> Unit,
        onLoadStartTimeChange: (Long) -> Unit,
    ) {
        Log.i(TAG, "Downloaded model ${message.label} (${message.buffer.capacity()} bytes)")
        titleState.value = message.label
        CoroutineScope(Dispatchers.IO).launch {
            when {
                message.label.endsWith(".zip") -> loadZip(
                    message = message,
                    modelViewer = modelViewer,
                    automation = automation,
                    statusToast = statusToast,
                    statusText = statusText,
                    onLoadStartFenceChange = onLoadStartFenceChange,
                    onLoadStartTimeChange = onLoadStartTimeChange,
                )

                message.label.endsWith(".hdr") -> loadHdr(
                    message,
                    modelViewer,
                    viewerContent,
                    statusToast,
                    statusText,
                )

                else -> loadGlb(
                    message,
                    modelViewer,
                    automation,
                    onLoadStartFenceChange,
                    onLoadStartTimeChange
                )
            }
        }
    }

    fun loadSettings(
        message: RemoteServer.ReceivedMessage,
        modelViewer: ModelViewer,
        automation: AutomationEngine,
        viewerContent: ViewerContent,
    ) {
        val json = StandardCharsets.UTF_8.decode(message.buffer).toString()
        viewerContent.assetLights = modelViewer.asset?.lightEntities
        automation.applySettings(modelViewer.engine, json, viewerContent)
        modelViewer.view.colorGrading = automation.getColorGrading(modelViewer.engine)
        modelViewer.cameraFocalLength = automation.viewerOptions.cameraFocalLength
        modelViewer.cameraNear = automation.viewerOptions.cameraNear
        modelViewer.cameraFar = automation.viewerOptions.cameraFar
        updateRootTransform(modelViewer, automation)
    }

    private fun updateRootTransform(modelViewer: ModelViewer, automation: AutomationEngine) {
        if (automation.viewerOptions.autoScaleEnabled) {
            modelViewer.transformToUnitCube()
        } else {
            modelViewer.clearRootTransform()
        }
    }

    inner class FrameCallback(
        private val choreographer: Choreographer,
        private val modelViewer: ModelViewer,
        private val automation: AutomationEngine,
        private val viewerContent: ViewerContent,
        private val remoteServer: RemoteServer?,
        private var loadStartTime: Long,
        private var statusToast: MutableState<Toast?>,
        private var statusText: MutableState<String?>,
        private var titleState: MutableState<String>,
    ) :
        Choreographer.FrameCallback {
        private val startTime = System.nanoTime()
        private var loadStartFence: Fence? = null
        private var latestDownload: String? = null
        override fun doFrame(frameTimeNanos: Long) {
            choreographer.postFrameCallback(this)

            loadStartFence?.let {
                if (it.wait(Fence.Mode.FLUSH, 0) == Fence.FenceStatus.CONDITION_SATISFIED) {
                    val end = System.nanoTime()
                    val total = (end - loadStartTime) / 1_000_000
                    Log.i(TAG, "The Filament backend took $total ms to load the model geometry.")
                    modelViewer.engine.destroyFence(it)
                    loadStartFence = null

                    val materials = mutableSetOf<Material>()
                    val rcm = modelViewer.engine.renderableManager
                    modelViewer.scene.forEach {
                        val entity = it
                        if (rcm.hasComponent(entity)) {
                            val ri = rcm.getInstance(entity)
                            val c = rcm.getPrimitiveCount(ri)
                            for (i in 0 until c) {
                                val mi = rcm.getMaterialInstanceAt(ri, i)
                                val ma = mi.material
                                materials.add(ma)
                            }
                        }
                    }
                    materials.forEach {
                        it.compile(
                            Material.CompilerPriorityQueue.HIGH,
                            Material.UserVariantFilterBit.DIRECTIONAL_LIGHTING or
                                    Material.UserVariantFilterBit.DYNAMIC_LIGHTING or
                                    Material.UserVariantFilterBit.SHADOW_RECEIVER,
                            null, null
                        )
                        it.compile(
                            Material.CompilerPriorityQueue.LOW,
                            Material.UserVariantFilterBit.FOG or
                                    Material.UserVariantFilterBit.SKINNING or
                                    Material.UserVariantFilterBit.SSR or
                                    Material.UserVariantFilterBit.VSM,
                            null, null
                        )
                    }
                }
            }

            modelViewer.animator?.apply {
                if (animationCount > 0) {
                    val elapsedTimeSeconds = (frameTimeNanos - startTime).toDouble() / 1_000_000_000
                    applyAnimation(0, elapsedTimeSeconds.toFloat())
                }
                updateBoneMatrices()
            }

            modelViewer.render(frameTimeNanos)

            // Check if a new download is in progress. If so, let the user know with toast.
            val currentDownload = remoteServer?.peekIncomingLabel()
            if (RemoteServer.isBinary(currentDownload) && currentDownload != latestDownload) {
                latestDownload = currentDownload
                Log.i(TAG, "Downloading $currentDownload")
                setStatusText("Downloading $currentDownload", statusToast, statusText)
            }

            // Check if a new message has been fully received from the client.
            val message = remoteServer?.acquireReceivedMessage()
            if (message != null) {
                if (message.label == latestDownload) {
                    latestDownload = null
                }
                if (RemoteServer.isJson(message.label)) {
                    loadSettings(
                        message = message,
                        modelViewer = modelViewer,
                        automation = automation,
                        viewerContent = viewerContent
                    )
                } else {
                    loadModelData(
                        message = message,
                        modelViewer = modelViewer,
                        viewerContent = viewerContent,
                        automation = automation,
                        statusToast = statusToast,
                        statusText = statusText,
                        titleState = titleState,
                        onLoadStartFenceChange = { loadStartFence = it },
                        onLoadStartTimeChange = { loadStartTime = it },
                    )
                }
            }
        }
    }

    // Just for testing purposes, this releases the current model and reloads the default model.
    inner class DoubleTapListener(
        private val modelViewer: ModelViewer,
        private val automation: AutomationEngine,
    ) : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            modelViewer.destroyModel()
            createDefaultRenderables(modelViewer, automation)
            return super.onDoubleTap(e)
        }
    }

    // Just for testing purposes
    inner class SingleTapListener(
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
}

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

