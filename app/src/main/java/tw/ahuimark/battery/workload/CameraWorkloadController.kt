package tw.ahuimark.battery.workload

import android.content.Context
import android.util.Range
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class CameraWorkloadController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val lensFacing: Int,
    private val quality: Quality
) {
    val previewView = PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        scaleType = PreviewView.ScaleType.FILL_CENTER
    }

    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val stopped = AtomicBoolean(false)
    private var provider: ProcessCameraProvider? = null
    private var recording: Recording? = null
    private var outputFile: File? = null

    fun start() {
        stopped.set(false)
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            if (stopped.get()) return@addListener
            runCatching {
                val cameraProvider = providerFuture.get()
                provider = cameraProvider
                val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(quality))
                    .build()
                val videoCapture = VideoCapture.Builder(recorder)
                    .setTargetFrameRate(Range(TARGET_FPS, TARGET_FPS))
                    .build()
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, videoCapture)

                val workloadDir = File(context.cacheDir, "camera-workload").apply { mkdirs() }
                val prefix = if (lensFacing == CameraSelector.LENS_FACING_BACK) "rear-uhd30" else "front-hd"
                outputFile = File(workloadDir, "$prefix-${System.currentTimeMillis()}.mp4")
                val outputOptions = FileOutputOptions.Builder(requireNotNull(outputFile)).build()
                recording = videoCapture.output
                    .prepareRecording(context, outputOptions)
                    .start(mainExecutor) { event ->
                        if (event is VideoRecordEvent.Finalize) {
                            outputFile?.delete()
                            outputFile = null
                        }
                    }
            }
        }, mainExecutor)
    }

    fun stop() {
        stopped.set(true)
        recording?.stop()
        recording = null
        provider?.unbindAll()
        provider = null
        outputFile?.delete()
        outputFile = null
    }

    private companion object {
        const val TARGET_FPS = 30
    }
}
