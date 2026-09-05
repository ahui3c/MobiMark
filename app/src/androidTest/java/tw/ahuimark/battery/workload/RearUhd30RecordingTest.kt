package tw.ahuimark.battery.workload

import android.media.MediaMetadataRetriever
import android.util.Range
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RearUhd30RecordingTest {
    @Test
    fun recordsRearCameraAtUhdWithThirtyFpsTarget() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val provider = ProcessCameraProvider.getInstance(context).get(15, TimeUnit.SECONDS)
        val lifecycleOwner = TestLifecycleOwner()
        val started = CountDownLatch(1)
        val finalized = CountDownLatch(1)
        val finalizeError = AtomicReference<Throwable?>()
        val recording = AtomicReference<Recording>()
        val output = File(context.cacheDir, "rear-uhd30-device-test.mp4").apply { delete() }

        instrumentation.runOnMainSync {
            lifecycleOwner.registry.currentState = Lifecycle.State.RESUMED
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.UHD))
                .build()
            val videoCapture = VideoCapture.Builder(recorder)
                .setTargetFrameRate(Range(30, 30))
                .build()
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                videoCapture
            )
            recording.set(
                videoCapture.output
                    .prepareRecording(context, FileOutputOptions.Builder(output).build())
                    .start(ContextCompat.getMainExecutor(context)) { event ->
                        when (event) {
                            is VideoRecordEvent.Start -> started.countDown()
                            is VideoRecordEvent.Finalize -> {
                                if (event.hasError()) {
                                    finalizeError.set(IllegalStateException("CameraX finalize error ${event.error}", event.cause))
                                }
                                finalized.countDown()
                            }
                        }
                    }
            )
        }

        try {
            assertTrue("CameraX recording did not start", started.await(15, TimeUnit.SECONDS))
            Thread.sleep(4_000)
            recording.get().stop()
            assertTrue("CameraX recording did not finalize", finalized.await(20, TimeUnit.SECONDS))
            assertNull(finalizeError.get())
            assertTrue("Recorded file is empty", output.length() > 0L)

            val metadata = MediaMetadataRetriever()
            try {
                metadata.setDataSource(output.absolutePath)
                val width = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt()
                val height = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt()
                assertEquals(3840, maxOf(width ?: 0, height ?: 0))
                assertEquals(2160, minOf(width ?: 0, height ?: 0))
            } finally {
                metadata.release()
            }
        } finally {
            instrumentation.runOnMainSync {
                provider.unbindAll()
                lifecycleOwner.registry.currentState = Lifecycle.State.DESTROYED
            }
            output.delete()
        }
    }

    private class TestLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle = registry
    }
}
