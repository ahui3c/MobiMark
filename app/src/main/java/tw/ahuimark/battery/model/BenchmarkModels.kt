package tw.ahuimark.battery.model

import java.util.UUID

enum class BenchmarkMode(val title: String, val description: String) {
    FULL("完整測試", "實測 80% → 20%，再換算等效 100% 續航"),
    QUICK("快速測試", "固定測試 4 小時，以實際耗電斜率換算")
}

enum class BenchmarkPhase {
    SETUP,
    PRECONDITIONING,
    RUNNING,
    COMPLETED,
    ABORTED
}

enum class WorkloadType(val title: String, val subtitle: String) {
    SHOOTER("3D 射擊", "Godot 第三人稱戰場 · 森林／都市／海岸 · 1080p 高更新率"),
    WEB("網頁瀏覽", "WebView 捲動、縮放與互動"),
    VIDEO("1080p 影音", "1920×1080 · 30 FPS · H.264"),
    CAMERA("4K 錄影", "後鏡頭 · UHD 3840×2160 · 30 FPS"),
    OFFICE("文書工作", "文字、PDF、資料排序與圖表"),
    // Deserialization only: never include this retired workload in a new session.
    VIDEO_CALL("視訊模擬（舊版）", "已移除，僅保留歷史成績")
}

val ACTIVE_WORKLOADS: List<WorkloadType> = listOf(
    WorkloadType.SHOOTER, WorkloadType.WEB, WorkloadType.VIDEO,
    WorkloadType.CAMERA, WorkloadType.OFFICE
)

data class BatterySample(
    val elapsedRealtimeMs: Long,
    val wallTimeMs: Long,
    val levelPercent: Int,
    val isCharging: Boolean,
    val temperatureCelsius: Float,
    val voltageMv: Int,
    val currentMicroAmps: Int?,
    val chargeCounterMicroAh: Int?
)

data class BenchmarkResult(
    val id: String = UUID.randomUUID().toString(),
    val mode: BenchmarkMode,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val measuredDurationMs: Long,
    val startLevel: Int,
    val endLevel: Int,
    val consumedPercent: Double,
    val equivalentFullDurationMs: Long,
    val completedLoops: Int,
    val maxTemperatureCelsius: Float,
    val isEstimated: Boolean,
    val recoveredFromCheckpoint: Boolean = false,
    val workloadStats: List<WorkloadEnergyStat> = emptyList(),
    val telemetryFileName: String? = null,
    val confidence: EstimateConfidence,
    val workloadVersion: String = WORKLOAD_VERSION,
    val interruptionProtection: String = "未記錄"
)

data class WorkloadEnergyStat(
    val workload: WorkloadType,
    val durationMs: Long,
    val consumedMilliAmpHours: Double,
    val estimatedConsumedPercent: Double,
    val averageCurrentMilliAmps: Double,
    val averageVoltageMv: Double,
    val maxTemperatureCelsius: Float,
    val sampleCount: Int
)

data class InterruptedSession(
    val sessionId: String,
    val mode: BenchmarkMode,
    val startedAtMs: Long,
    val lastSavedAtMs: Long,
    val measuredDurationMs: Long,
    val startLevel: Int = 80,
    val lastLevel: Int,
    val startChargeCounterMicroAh: Int?,
    val lastChargeCounterMicroAh: Int?,
    val completedLoops: Int,
    val maxTemperatureCelsius: Float,
    val currentWorkload: WorkloadType,
    val workloadElapsedMs: Long,
    val workloadStats: List<WorkloadEnergyStat> = emptyList(),
    val telemetryFileName: String? = null,
    val workloadVersion: String = WORKLOAD_VERSION,
    val interruptionProtection: String = "未記錄"
)

enum class EstimateConfidence(val label: String) {
    MEASURED("完整實測換算"),
    HIGH("高信賴度"),
    MEDIUM("中等信賴度"),
    LOW("低信賴度")
}

data class DeviceReadiness(
    val batteryAbove80: Boolean = false,
    val unplugged: Boolean = false,
    val cameraPermission: Boolean = false,
    val rearUhd30Supported: Boolean = false,
    val mediaAssetReady: Boolean = false,
    val storageReady: Boolean = false,
    val automaticBrightnessDisabled: Boolean = false,
    val mediaAssetPath: String = "",
    val availableStorageBytes: Long = 0L
) {
    val canStart: Boolean
        get() = batteryAbove80 && unplugged && cameraPermission && rearUhd30Supported &&
            mediaAssetReady && storageReady
}

data class BenchmarkUiState(
    val mode: BenchmarkMode = BenchmarkMode.FULL,
    val phase: BenchmarkPhase = BenchmarkPhase.SETUP,
    val battery: BatterySample? = null,
    val readiness: DeviceReadiness = DeviceReadiness(),
    val currentWorkload: WorkloadType = WorkloadType.SHOOTER,
    val workloadIndex: Int = 0,
    val elapsedOfficialMs: Long = 0L,
    val elapsedPreconditioningMs: Long = 0L,
    val workloadElapsedMs: Long = 0L,
    val completedLoops: Int = 0,
    val maxTemperatureCelsius: Float = 0f,
    val abortReason: String? = null,
    val result: BenchmarkResult? = null,
    val history: List<BenchmarkResult> = emptyList(),
    val interruptedSession: InterruptedSession? = null,
    val recoveryError: String? = null,
    val lastCheckpointAtMs: Long? = null,
    val checkpointError: String? = null,
    val settings: BenchmarkSettings = BenchmarkSettings(),
    val mediaDownloadProgress: Float? = null,
    val mediaDownloadError: String? = null,
    val dndAccessGranted: Boolean = false,
    val dndError: String? = null,
    val volumeError: String? = null,
    val dndRecoveryPending: Boolean = false,
    val interruptionProtection: String = "未啟用"
) {
    val canStart: Boolean
        get() = readiness.batteryAbove80 && readiness.unplugged && readiness.cameraPermission &&
            readiness.rearUhd30Supported && readiness.storageReady &&
            settings.isValid && interruptedSession == null &&
            (!settings.automaticDnd || dndAccessGranted) && !dndRecoveryPending &&
            (settings.videoSourceMode == VideoSourceMode.ONLINE || readiness.mediaAssetReady)
}

const val WORKLOAD_DURATION_MS = 3L * 60L * 1000L
const val WEB_WORKLOAD_DURATION_MS = 3L * 60L * 1000L
const val WEB_PAGE_DURATION_MS = 60L * 1000L
const val QUICK_TEST_DURATION_MS = 4L * 60L * 60L * 1000L
const val LOOP_DURATION_MS = WORKLOAD_DURATION_MS * 5L
const val WORKLOAD_VERSION = "Ahuimark Workload 4.0 · Five workloads · Godot 4.7.2 · Office UX"
