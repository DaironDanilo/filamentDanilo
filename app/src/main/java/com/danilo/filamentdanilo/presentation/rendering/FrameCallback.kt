package com.danilo.filamentdanilo.presentation.rendering

import android.content.Context
import android.util.Log
import android.view.Choreographer
import com.danilo.filamentdanilo.MainActivity.Companion.TAG
import com.google.android.filament.Fence
import com.google.android.filament.IndirectLight
import com.google.android.filament.Material
import com.google.android.filament.Skybox
import com.google.android.filament.utils.AutomationEngine
import com.google.android.filament.utils.AutomationEngine.ViewerContent
import com.google.android.filament.utils.HDRLoader
import com.google.android.filament.utils.IBLPrefilterContext
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.RemoteServer
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

class FrameCallback(
    private val choreographer: Choreographer,
    private val modelViewer: ModelViewer,
    private val automation: AutomationEngine,
    private val viewerContent: ViewerContent,
    private val remoteServer: RemoteServer?,
    private var loadStartTime: Long,
    private var onToastMessageChange: (String) -> Unit,
    private var onTitleChange: (String) -> Unit,
    private var context: Context,
    private var updateRootTransform: (ModelViewer, AutomationEngine) -> Unit,
) : Choreographer.FrameCallback {
    private val startTime = System.nanoTime()
    private var loadStartFence: Fence? = null
    private var latestDownload: String? = null

    private suspend fun loadHdr(
        message: RemoteServer.ReceivedMessage,
        modelViewer: ModelViewer,
        viewerContent: ViewerContent,
    ) {
        withContext(Dispatchers.Main) {
            val engine = modelViewer.engine
            val equirect = HDRLoader.createTexture(engine, message.buffer)
            if (equirect == null) {
                onToastMessageChange("Could not decode HDR file.")
            } else {
                onToastMessageChange("Successfully decoded HDR file.")

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

    private fun loadSettings(
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

    private suspend fun loadZip(
        message: RemoteServer.ReceivedMessage,
        modelViewer: ModelViewer,
        automation: AutomationEngine,
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
            val file = File.createTempFile("incoming", "zip", context.cacheDir)
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
            onToastMessageChange("Could not find .gltf or .glb in the zip.")
            return
        }

        if (outOfMemory != null) {
            onToastMessageChange("Out of memory while deflating $outOfMemory")
            return
        }

        val gltfBuffer = pathToBufferMapping[gltfPath]!!

        // In a zip file, the gltf file might be in the same folder as resources, or in a different
        // folder. It is crucial to test against both of these cases. In any case, the resource
        // paths are all specified relative to the location of the gltf file.
        val prefix = URI(gltfPath!!).resolve(".")

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
                        onToastMessageChange("Zip is missing $path")
                    }
                    pathToBufferMapping[path]
                }
            }
            updateRootTransform(modelViewer, automation)
            onLoadStartTimeChange(System.nanoTime())
            onLoadStartFenceChange(modelViewer.engine.createFence())
        }
    }

    private fun loadModelData(
        message: RemoteServer.ReceivedMessage,
        modelViewer: ModelViewer,
        viewerContent: ViewerContent,
        automation: AutomationEngine,
        onTitleChange: (String) -> Unit,
        onLoadStartFenceChange: (Fence) -> Unit,
        onLoadStartTimeChange: (Long) -> Unit,
    ) {
        Log.i(TAG, "Downloaded model ${message.label} (${message.buffer.capacity()} bytes)")
        onTitleChange(message.label)
        CoroutineScope(Dispatchers.IO).launch {
            when {
                message.label.endsWith(".zip") -> loadZip(
                    message = message,
                    modelViewer = modelViewer,
                    automation = automation,
                    onLoadStartFenceChange = onLoadStartFenceChange,
                    onLoadStartTimeChange = onLoadStartTimeChange,
                )

                message.label.endsWith(".hdr") -> loadHdr(
                    message,
                    modelViewer,
                    viewerContent,
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
            onToastMessageChange("Downloading $currentDownload")
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
                    onTitleChange = onTitleChange,
                    onLoadStartFenceChange = { loadStartFence = it },
                    onLoadStartTimeChange = { loadStartTime = it },
                )
            }
        }
    }
}
