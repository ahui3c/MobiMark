package tw.ahuimark.battery.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.CamcorderProfile
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaRecorder
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import tw.ahuimark.battery.model.BatterySample
import tw.ahuimark.battery.model.DeviceReadiness
import tw.ahuimark.battery.media.VideoMaterial
import java.io.File
import java.security.MessageDigest

class CapabilityChecker(private val context: Context) {
    private var cachedMediaSignature: String? = null
    private var cachedMediaValid: Boolean = false

    fun check(battery: BatterySample): DeviceReadiness {
        val mediaFile = File(context.filesDir, "media/${VideoMaterial.FILE_NAME}")
        val available = runCatching {
            context.getSystemService(StorageManager::class.java)
                .getAllocatableBytes(StorageManager.UUID_DEFAULT)
        }.getOrElse { context.filesDir.usableSpace }
        return DeviceReadiness(
            batteryAbove80 = battery.levelPercent > 80,
            unplugged = !battery.isCharging,
            cameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
            rearUhd30Supported = hasUhd30Profile(),
            mediaAssetReady = isStandardMediaAsset(mediaFile),
            storageReady = available >= REQUIRED_STORAGE_BYTES,
            automaticBrightnessDisabled = runCatching {
                Settings.System.getInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE
                ) == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            }.getOrDefault(false),
            mediaAssetPath = mediaFile.absolutePath,
            availableStorageBytes = available
        )
    }

    @Suppress("DEPRECATION")
    private fun hasUhd30Profile(): Boolean {
        val cameraManager = context.getSystemService(CameraManager::class.java)
        val rearCameraIds = runCatching {
            cameraManager.cameraIdList.filter { cameraId ->
                cameraManager.getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
        }.getOrDefault(emptyList())

        // The no-camera-id CamcorderProfile API only checks the platform's
        // default camera. On Pixel and logical multi-camera devices that camera
        // is not guaranteed to be the rear main camera selected by CameraX.
        return rearCameraIds.any { cameraId ->
            val characteristics = runCatching {
                cameraManager.getCameraCharacteristics(cameraId)
            }.getOrNull()
            val recorderProfileSupported = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    CamcorderProfile.getAll(cameraId, CamcorderProfile.QUALITY_2160P)
                        ?.videoProfiles
                        ?.any { profile ->
                            isUhd30Profile(profile.width, profile.height, profile.frameRate)
                        } == true
                } else {
                    val numericId = cameraId.toIntOrNull() ?: return@runCatching false
                    CamcorderProfile.hasProfile(numericId, CamcorderProfile.QUALITY_2160P) &&
                        CamcorderProfile.get(numericId, CamcorderProfile.QUALITY_2160P).let { profile ->
                            isUhd30Profile(
                                profile.videoFrameWidth,
                                profile.videoFrameHeight,
                                profile.videoFrameRate
                            )
                        }
                }
            }.getOrDefault(false)
            recorderProfileSupported || characteristics?.supportsUhd30RecorderStream() == true
        }
    }

    private fun CameraCharacteristics.supportsUhd30RecorderStream(): Boolean {
        val configuration = get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return false
        val uhdSizes = runCatching {
            configuration.getOutputSizes(MediaRecorder::class.java).orEmpty()
        }.getOrDefault(emptyArray())
        val hasThirtyFpsRange = get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            .orEmpty()
            .any { range -> range.lower <= 30 && range.upper >= 30 }
        if (!hasThirtyFpsRange) return false
        return uhdSizes.any { size ->
            if (!isUhdSize(size.width, size.height)) return@any false
            val minimumFrameDurationNs = runCatching {
                configuration.getOutputMinFrameDuration(MediaRecorder::class.java, size)
            }.getOrDefault(0L)
            minimumFrameDurationNs <= 0L || minimumFrameDurationNs <= UHD_30_MAX_FRAME_DURATION_NS
        }
    }

    private fun isStandardMediaAsset(file: File): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        if (file.length() != VideoMaterial.EXPECTED_SIZE_BYTES) return false
        val signature = "${file.absolutePath}|${file.length()}|${file.lastModified()}"
        if (signature == cachedMediaSignature) return cachedMediaValid
        val valid = runCatching {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(file.absolutePath)
                val formatMatches = (0 until extractor.trackCount).any { index ->
                    val format = extractor.getTrackFormat(index)
                    val mime = format.getString(MediaFormat.KEY_MIME)
                    val width = format.intOrNull(MediaFormat.KEY_WIDTH)
                    val height = format.intOrNull(MediaFormat.KEY_HEIGHT)
                    val frameRate = format.intOrNull(MediaFormat.KEY_FRAME_RATE)
                    mime == AVC_MIME && width == 1920 && height == 1080 && frameRate in 29..31
                }
                formatMatches && file.sha256() == VideoMaterial.EXPECTED_SHA256
            } finally {
                extractor.release()
            }
        }.getOrDefault(false)
        cachedMediaSignature = signature
        cachedMediaValid = valid
        return valid
    }

    private fun MediaFormat.intOrNull(key: String): Int? =
        if (containsKey(key)) getInteger(key) else null

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02X".format(it) }
    }

    companion object {
        const val REQUIRED_STORAGE_BYTES = 3L * 1024L * 1024L * 1024L
        private const val AVC_MIME = "video/avc"
        private const val UHD_30_MAX_FRAME_DURATION_NS = 33_666_667L
    }
}

internal fun isUhd30Profile(width: Int, height: Int, frameRate: Int): Boolean =
    isUhdSize(width, height) && frameRate >= 29

internal fun isUhdSize(width: Int, height: Int): Boolean =
    maxOf(width, height) >= 3840 && minOf(width, height) >= 2160
