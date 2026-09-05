package tw.ahuimark.battery

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import tw.ahuimark.battery.core.BatteryMonitor
import tw.ahuimark.battery.core.BenchmarkSchedule
import tw.ahuimark.battery.core.CapabilityChecker
import tw.ahuimark.battery.core.ScoreCalculator
import tw.ahuimark.battery.core.WorkloadEnergyAccumulator
import tw.ahuimark.battery.data.ResultStore
import tw.ahuimark.battery.data.SettingsStore
import tw.ahuimark.battery.data.ActiveSessionStore
import tw.ahuimark.battery.data.TelemetryRecorder
import tw.ahuimark.battery.model.BenchmarkSettings
import tw.ahuimark.battery.model.BenchmarkMode
import tw.ahuimark.battery.model.BenchmarkPhase
import tw.ahuimark.battery.model.BenchmarkResult
import tw.ahuimark.battery.model.BenchmarkUiState
import tw.ahuimark.battery.model.InterruptedSession
import tw.ahuimark.battery.model.QUICK_TEST_DURATION_MS
import tw.ahuimark.battery.model.WorkloadType
import tw.ahuimark.battery.model.VideoSourceMode
import tw.ahuimark.battery.model.WebSourceMode
import tw.ahuimark.battery.media.MediaAssetInstaller
import tw.ahuimark.battery.service.BenchmarkService
import kotlin.math.max

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val batteryMonitor = BatteryMonitor(application)
    private val capabilityChecker = CapabilityChecker(application)
    private val resultStore = ResultStore(application)
    private val settingsStore = SettingsStore(application)
    private val activeSessionStore = ActiveSessionStore(application)
    private val mediaAssetInstaller = MediaAssetInstaller(application)
    private val telemetryRecorder = TelemetryRecorder(application)
    private val energyAccumulator = WorkloadEnergyAccumulator()
    private val dnd = tw.ahuimark.battery.core.TestDndController(application)
    private val volume = tw.ahuimark.battery.core.TestVolumeController(application)
    private val _uiState = MutableStateFlow(
        BenchmarkUiState(
            history = resultStore.load(),
            settings = settingsStore.load(),
            interruptedSession = activeSessionStore.load()
        )
    )
    val uiState: StateFlow<BenchmarkUiState> = _uiState.asStateFlow()

    private var ticker: Job? = null
    private var sessionStartElapsedMs: Long? = null
    private var officialStartElapsedMs: Long? = null
    private var officialStartWallMs: Long? = null
    private var officialStartChargeCounterMicroAh: Int? = null
    private var lastCheckpointBucket: Long = -1L
    private var activeSessionId: String? = null
    private var telemetryFileName: String? = null

    init {
        restoreDnd("app_start_recovery")
        restoreVolume()
        ticker = viewModelScope.launch {
            while (isActive) {
                updateFromDevice()
                delay(1_000)
            }
        }
    }

    fun selectMode(mode: BenchmarkMode) {
        if (_uiState.value.phase == BenchmarkPhase.SETUP) {
            _uiState.update { it.copy(mode = mode) }
        }
    }

    fun refreshReadiness() = updateFromDevice()

    fun onAppForegrounded() {
        if (_uiState.value.phase == BenchmarkPhase.SETUP) muteVolume()
    }

    private fun muteVolume(): Boolean {
        val result = volume.mute()
        _uiState.update { it.copy(volumeError = result.exceptionOrNull()?.let { e -> "媒體音量調整失敗：${e.message}" }) }
        return result.isSuccess
    }

    private fun restoreVolume() {
        val result = volume.restore()
        _uiState.update { it.copy(volumeError = result.exceptionOrNull()?.let { e -> "媒體音量復原失敗：${e.message}" }) }
    }

    fun setAutomaticDnd(enabled: Boolean) {
        if (_uiState.value.phase == BenchmarkPhase.SETUP) {
            updateSettings { it.copy(automaticDnd = enabled) }
        }
    }

    fun retryDndRecovery() {
        if (_uiState.value.phase == BenchmarkPhase.SETUP) restoreDnd("manual_retry")
        updateFromDevice()
    }

    private fun restoreDnd(reason: String) {
        val restored = dnd.end(reason)
        _uiState.update { it.copy(dndError = restored.exceptionOrNull()?.message,
            dndRecoveryPending = dnd.hasPendingRecovery, dndAccessGranted = dnd.hasAccess) }
    }

    fun updateWebUrl(index: Int, value: String) {
        if (_uiState.value.phase != BenchmarkPhase.SETUP || index !in 0..2) return
        updateSettings { settings ->
            val urls = settings.webUrls.toMutableList().apply {
                while (size < 3) add("")
                this[index] = value
            }
            settings.copy(webUrls = urls)
        }
    }

    fun selectWebSource(mode: WebSourceMode) {
        if (_uiState.value.phase == BenchmarkPhase.SETUP) {
            updateSettings { it.copy(webSourceMode = mode) }
        }
    }

    fun selectVideoSource(mode: VideoSourceMode) {
        if (_uiState.value.phase == BenchmarkPhase.SETUP) {
            updateSettings { it.copy(videoSourceMode = mode) }
        }
    }

    fun updateOnlineVideoUrl(value: String) {
        if (_uiState.value.phase == BenchmarkPhase.SETUP) {
            updateSettings { it.copy(onlineVideoUrl = value) }
        }
    }

    private fun updateSettings(transform: (BenchmarkSettings) -> BenchmarkSettings) {
        val settings = transform(_uiState.value.settings)
        settingsStore.save(settings)
        _uiState.update { it.copy(settings = settings) }
    }

    fun downloadVideoMaterial() {
        if (_uiState.value.mediaDownloadProgress != null) return
        _uiState.update { it.copy(mediaDownloadProgress = 0f, mediaDownloadError = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                mediaAssetInstaller.download { progress ->
                    _uiState.update { it.copy(mediaDownloadProgress = progress) }
                }
            }
            _uiState.update {
                it.copy(
                    mediaDownloadProgress = null,
                    mediaDownloadError = result.exceptionOrNull()?.message
                )
            }
            updateFromDevice()
        }
    }

    fun importVideo(uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val app = getApplication<Application>()
                val target = File(app.filesDir, "media/ahuimark_4k_test.mp4")
                target.parentFile?.mkdirs()
                val temporary = File(target.parentFile, "ahuimark_4k_test.importing")
                runCatching {
                    app.contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "無法開啟影片" }
                        temporary.outputStream().use { output -> input.copyTo(output) }
                    }
                    require(temporary.length() > 0) { "影片檔案為空" }
                    if (target.exists()) target.delete()
                    require(temporary.renameTo(target)) { "無法保存影片" }
                }.onFailure { temporary.delete() }
            }
            updateFromDevice()
        }
    }

    fun startBenchmark() {
        val state = _uiState.value
        if (state.phase != BenchmarkPhase.SETUP || !state.canStart) return
        if (!muteVolume()) return

        val sessionId = UUID.randomUUID().toString()
        val protection = dnd.begin(sessionId, state.settings.automaticDnd)
        if (protection.isFailure) {
            _uiState.update { it.copy(dndError = protection.exceptionOrNull()?.message,
                dndRecoveryPending = dnd.hasPendingRecovery, dndAccessGranted = dnd.hasAccess) }
            return
        }
        _uiState.update { it.copy(dndError = null,
            interruptionProtection = if (protection.getOrThrow()) "已要求啟用測試勿擾" else "未啟用（設定關閉）") }

        sessionStartElapsedMs = state.battery?.elapsedRealtimeMs
        officialStartElapsedMs = null
        officialStartWallMs = null
        officialStartChargeCounterMicroAh = null
        lastCheckpointBucket = -1L
        activeSessionStore.clear()
        activeSessionId = sessionId
        try {
            telemetryFileName = telemetryRecorder.start(sessionId)
        } catch (error: Exception) {
            restoreDnd("record_start_failed")
            restoreVolume()
            _uiState.update { it.copy(dndError = "測試紀錄建立失敗：${error.message}") }
            return
        }
        _uiState.update {
            it.copy(
                phase = BenchmarkPhase.PRECONDITIONING,
                currentWorkload = WorkloadType.SHOOTER,
                workloadIndex = 0,
                elapsedOfficialMs = 0,
                elapsedPreconditioningMs = 0,
                workloadElapsedMs = 0,
                completedLoops = 0,
                maxTemperatureCelsius = it.battery?.temperatureCelsius ?: 0f,
                lastCheckpointAtMs = null,
                checkpointError = null,
                abortReason = null,
                result = null
            )
        }
        val app = getApplication<Application>()
        try {
            app.startForegroundService(Intent(app, BenchmarkService::class.java))
        } catch (error: Exception) {
            abort("測試服務啟動失敗：${error.message}")
        }
    }

    fun stopBenchmark(reason: String = "使用者停止測試") {
        val phase = _uiState.value.phase
        if (phase == BenchmarkPhase.PRECONDITIONING || phase == BenchmarkPhase.RUNNING) {
            abort(reason)
        }
    }

    fun recoverInterruptedBenchmark() {
        val snapshot = _uiState.value.interruptedSession ?: return
        val result = ScoreCalculator.recoveredResult(snapshot, MIN_RECOVERY_DURATION_MS)
        if (result == null) {
            _uiState.update {
                it.copy(recoveryError = "紀錄時間或有效耗電量不足，暫時無法推算；可保留紀錄或選擇捨棄。")
            }
            return
        }
        resultStore.save(result)
        activeSessionStore.clear()
        _uiState.update {
            it.copy(
                phase = BenchmarkPhase.COMPLETED,
                mode = snapshot.mode,
                result = result,
                history = resultStore.load(),
                interruptedSession = null,
                recoveryError = null
            )
        }
    }

    fun discardInterruptedBenchmark() {
        val telemetry = _uiState.value.interruptedSession?.telemetryFileName
        activeSessionStore.clear()
        telemetryRecorder.delete(telemetry)
        _uiState.update { it.copy(interruptedSession = null, recoveryError = null) }
    }

    fun openSavedResult(result: BenchmarkResult) {
        val state = _uiState.value
        if (state.phase != BenchmarkPhase.SETUP || state.history.none { it.id == result.id }) return
        _uiState.update {
            it.copy(phase = BenchmarkPhase.COMPLETED, mode = result.mode, result = result)
        }
    }

    fun deleteSavedResult(result: BenchmarkResult) {
        if (_uiState.value.phase != BenchmarkPhase.SETUP) return
        val removed = resultStore.delete(result.id) ?: return
        telemetryRecorder.delete(removed.telemetryFileName)
        File(getApplication<Application>().filesDir, "test-events/${removed.id}.jsonl").delete()
        _uiState.update { it.copy(history = resultStore.load()) }
    }

    fun reset() {
        stopService()
        sessionStartElapsedMs = null
        officialStartElapsedMs = null
        officialStartWallMs = null
        officialStartChargeCounterMicroAh = null
        lastCheckpointBucket = -1L
        activeSessionId = null
        telemetryFileName = null
        val battery = batteryMonitor.read()
        _uiState.value = BenchmarkUiState(
            mode = _uiState.value.mode,
            settings = _uiState.value.settings,
            battery = battery,
            readiness = capabilityChecker.check(battery),
            dndAccessGranted = dnd.hasAccess,
            dndRecoveryPending = dnd.hasPendingRecovery,
            dndError = _uiState.value.dndError,
            volumeError = _uiState.value.volumeError,
            history = resultStore.load()
        )
    }

    fun onAppBackgrounded() {
        restoreVolume()
        if (_uiState.value.phase == BenchmarkPhase.RUNNING ||
            _uiState.value.phase == BenchmarkPhase.PRECONDITIONING
        ) {
            dnd.event("app_backgrounded", "測試期間 App 離開前景")
            abort("測試期間 App 離開前景")
        }
    }

    private fun updateFromDevice() {
        val sample = batteryMonitor.read()
        val current = _uiState.value
        val readiness = capabilityChecker.check(sample)

        _uiState.update {
            it.copy(
                battery = sample,
                readiness = readiness,
                dndAccessGranted = dnd.hasAccess,
                maxTemperatureCelsius = max(it.maxTemperatureCelsius, sample.temperatureCelsius)
            )
        }
        if (current.phase == BenchmarkPhase.PRECONDITIONING || current.phase == BenchmarkPhase.RUNNING) {
            if (current.settings.automaticDnd) {
                if (dnd.isProtectionActive()) {
                    if (current.interruptionProtection != "測試專用勿擾已啟用") {
                        dnd.event("activation_verified")
                        _uiState.update { it.copy(interruptionProtection = "測試專用勿擾已啟用") }
                    }
                } else if (sample.elapsedRealtimeMs - (sessionStartElapsedMs ?: sample.elapsedRealtimeMs) >= 5_000L) {
                    abort("測試勿擾未生效、已被停用或權限已撤銷")
                    return
                }
            }
            when {
                current.settings.automaticDnd && !dnd.hasAccess -> abort("勿擾模式權限已撤銷")
                sample.isCharging -> abort("偵測到充電來源")
                sample.temperatureCelsius >= MAX_SAFE_BATTERY_TEMP_C -> abort("電池溫度達安全上限")
                current.phase == BenchmarkPhase.PRECONDITIONING && sample.levelPercent <= 80 -> beginOfficial(sample)
                current.phase == BenchmarkPhase.PRECONDITIONING -> updatePreconditioning(sample)
                current.phase == BenchmarkPhase.RUNNING -> updateRunning(sample)
            }
        }
    }

    private fun updatePreconditioning(sample: tw.ahuimark.battery.model.BatterySample) {
        val started = sessionStartElapsedMs ?: sample.elapsedRealtimeMs.also { sessionStartElapsedMs = it }
        val elapsed = (sample.elapsedRealtimeMs - started).coerceAtLeast(0L)
        val position = BenchmarkSchedule.positionAt(elapsed)
        _uiState.update {
            it.copy(
                elapsedPreconditioningMs = elapsed,
                workloadElapsedMs = position.elapsedInWorkloadMs,
                workloadIndex = position.workloadIndex,
                currentWorkload = position.workload,
                completedLoops = position.completedLoops
            )
        }
        telemetryRecorder.append(
            elapsed,
            BenchmarkPhase.PRECONDITIONING,
            position.workload,
            position.elapsedInWorkloadMs,
            sample
        )
    }

    private fun beginOfficial(sample: tw.ahuimark.battery.model.BatterySample) {
        officialStartElapsedMs = sample.elapsedRealtimeMs
        officialStartWallMs = sample.wallTimeMs
        officialStartChargeCounterMicroAh = sample.chargeCounterMicroAh
        lastCheckpointBucket = -1L
        energyAccumulator.reset(sample)
        _uiState.update {
            it.copy(
                phase = BenchmarkPhase.RUNNING,
                elapsedOfficialMs = 0L,
                workloadElapsedMs = 0L,
                workloadIndex = 0,
                currentWorkload = WorkloadType.SHOOTER
            )
        }
        energyAccumulator.record(WorkloadType.SHOOTER, 0L, sample)
        telemetryRecorder.append(0L, BenchmarkPhase.RUNNING, WorkloadType.SHOOTER, 0L, sample)
        persistCheckpoint(sample, 0L, force = true)
    }

    private fun updateRunning(sample: tw.ahuimark.battery.model.BatterySample) {
        val started = officialStartElapsedMs ?: return
        val elapsed = (sample.elapsedRealtimeMs - started).coerceAtLeast(0L)
        val position = BenchmarkSchedule.positionAt(elapsed)

        _uiState.update {
            it.copy(
                elapsedOfficialMs = elapsed,
                workloadElapsedMs = position.elapsedInWorkloadMs,
                workloadIndex = position.workloadIndex,
                currentWorkload = position.workload,
                completedLoops = position.completedLoops
            )
        }
        energyAccumulator.record(position.workload, elapsed, sample)
        telemetryRecorder.append(
            elapsed,
            BenchmarkPhase.RUNNING,
            position.workload,
            position.elapsedInWorkloadMs,
            sample
        )
        persistCheckpoint(sample, elapsed)

        val mode = _uiState.value.mode
        when {
            sample.levelPercent <= 20 -> complete(sample, elapsed, reached20 = true)
            mode == BenchmarkMode.QUICK && elapsed >= QUICK_TEST_DURATION_MS ->
                complete(sample, QUICK_TEST_DURATION_MS, reached20 = false)
        }
    }

    private fun complete(
        sample: tw.ahuimark.battery.model.BatterySample,
        durationMs: Long,
        reached20: Boolean
    ) {
        val consumed = (80 - sample.levelPercent).toDouble().coerceAtLeast(0.0)
        if (durationMs <= 0 || consumed <= 0) {
            abort("有效耗電量不足，無法計算成績")
            return
        }
        val state = _uiState.value
        val fullRun = reached20 && consumed >= 60.0
        dnd.event("test_completed")
        restoreDnd("completed")
        restoreVolume()
        val result = BenchmarkResult(
            id = activeSessionId ?: UUID.randomUUID().toString(),
            mode = state.mode,
            startedAtMs = officialStartWallMs ?: sample.wallTimeMs - durationMs,
            endedAtMs = sample.wallTimeMs,
            measuredDurationMs = durationMs,
            startLevel = 80,
            endLevel = sample.levelPercent,
            consumedPercent = consumed,
            equivalentFullDurationMs = ScoreCalculator.equivalentFullDurationMs(durationMs, consumed),
            completedLoops = state.completedLoops,
            maxTemperatureCelsius = state.maxTemperatureCelsius,
            isEstimated = !fullRun,
            workloadStats = energyAccumulator.snapshot(),
            telemetryFileName = telemetryFileName,
            confidence = ScoreCalculator.confidence(consumed, fullRun),
            interruptionProtection = state.interruptionProtection
        )
        telemetryRecorder.close()
        resultStore.save(result)
        activeSessionStore.clear()
        _uiState.update {
            it.copy(phase = BenchmarkPhase.COMPLETED, result = result, history = resultStore.load())
        }
        stopService()
    }

    private fun abort(reason: String) {
        dnd.event("test_aborted", reason)
        restoreDnd("aborted")
        restoreVolume()
        activeSessionStore.clear()
        telemetryRecorder.delete(telemetryFileName)
        _uiState.update { it.copy(phase = BenchmarkPhase.ABORTED, abortReason = reason) }
        stopService()
    }

    private fun stopService() {
        restoreDnd("test_finished_or_stopped")
        restoreVolume()
        val app = getApplication<Application>()
        app.stopService(Intent(app, BenchmarkService::class.java))
    }

    private fun persistCheckpoint(
        sample: tw.ahuimark.battery.model.BatterySample,
        elapsedMs: Long,
        force: Boolean = false
    ) {
        val bucket = elapsedMs / CHECKPOINT_INTERVAL_MS
        if (!force && bucket <= lastCheckpointBucket) return
        val state = _uiState.value
        val startedAt = officialStartWallMs ?: return
        val saved = activeSessionStore.save(
            InterruptedSession(
                sessionId = activeSessionId ?: return,
                mode = state.mode,
                startedAtMs = startedAt,
                lastSavedAtMs = sample.wallTimeMs,
                measuredDurationMs = elapsedMs,
                lastLevel = sample.levelPercent,
                startChargeCounterMicroAh = officialStartChargeCounterMicroAh,
                lastChargeCounterMicroAh = sample.chargeCounterMicroAh,
                completedLoops = state.completedLoops,
                maxTemperatureCelsius = state.maxTemperatureCelsius,
                currentWorkload = state.currentWorkload,
                workloadElapsedMs = state.workloadElapsedMs,
                workloadStats = energyAccumulator.snapshot(),
                telemetryFileName = telemetryFileName,
                interruptionProtection = state.interruptionProtection
            )
        )
        if (saved) {
            lastCheckpointBucket = bucket
            _uiState.update { it.copy(lastCheckpointAtMs = sample.wallTimeMs, checkpointError = null) }
        } else {
            _uiState.update { it.copy(checkpointError = "備援紀錄寫入失敗") }
        }
    }

    override fun onCleared() {
        stopService()
        ticker?.cancel()
        telemetryRecorder.close()
        super.onCleared()
    }

    companion object {
        private const val MAX_SAFE_BATTERY_TEMP_C = 48f
        private const val CHECKPOINT_INTERVAL_MS = 30_000L
        private const val MIN_RECOVERY_DURATION_MS = 60_000L
    }
}
