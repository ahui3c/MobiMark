package tw.ahuimark.battery.debug

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import tw.ahuimark.battery.model.BenchmarkSettings
import tw.ahuimark.battery.model.VideoSourceMode
import tw.ahuimark.battery.model.WorkloadType
import tw.ahuimark.battery.ui.theme.AhuimarkTheme
import tw.ahuimark.battery.workload.WorkloadHost

class WorkloadPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        @Suppress("DEPRECATION")
        val panel = windowManager.defaultDisplay
        val current = panel.mode
        panel.supportedModes.filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate }?.let { mode ->
                window.attributes = window.attributes.apply { preferredDisplayModeId = mode.modeId; preferredRefreshRate = mode.refreshRate }
            }
        val offset = intent.getLongExtra("elapsed_ms", 0L)
        val workload = runCatching {
            WorkloadType.valueOf(intent.getStringExtra("workload") ?: WorkloadType.WEB.name)
        }.getOrDefault(WorkloadType.WEB)
        if (workload !in tw.ahuimark.battery.model.ACTIVE_WORKLOADS) {
            finish()
            return
        }
        val online = intent.getBooleanExtra("online", false)
        requestedOrientation = if (workload == WorkloadType.VIDEO || workload == WorkloadType.SHOOTER) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        setContent {
            AhuimarkTheme {
                val started = remember { SystemClock.elapsedRealtime() }
                var elapsed by remember { mutableLongStateOf(offset) }
                LaunchedEffect(Unit) {
                    while (true) {
                        elapsed = offset + SystemClock.elapsedRealtime() - started
                        delay(250L)
                    }
                }
                if (intent.getBooleanExtra("chrome", false)) {
                    tw.ahuimark.battery.ui.RunningScreen(
                        tw.ahuimark.battery.model.BenchmarkUiState(
                            phase = tw.ahuimark.battery.model.BenchmarkPhase.PRECONDITIONING,
                            currentWorkload = workload,
                            workloadIndex = workload.ordinal,
                            workloadElapsedMs = elapsed,
                            elapsedOfficialMs = elapsed
                        ), onStop = { finish() }
                    )
                } else WorkloadHost(
                    type = workload,
                    elapsedMs = elapsed,
                    settings = BenchmarkSettings(
                        videoSourceMode = if (online) VideoSourceMode.ONLINE else VideoSourceMode.LOCAL
                    )
                )
            }
        }
    }
}
